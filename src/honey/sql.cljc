;; copyright (c) 2020-2021 sean corfield, all rights reserved

(ns honey.sql
  "Primary API for HoneySQL 2.x.

  This includes the `format` function -- the primary entry point -- as well
  as several public formatters that are intended to help users extend the
  supported syntax.

  In addition, functions to extend HoneySQL are also provided here:
  * `sql-kw` -- turns a Clojure keyword into SQL code (makes it uppercase
        and replaces - with space).
  * `format-dsl` -- intended to format SQL statements; returns a vector
        containing a SQL string followed by parameter values.
  * `format-expr` -- intended to format SQL expressions; returns a vector
        containing a SQL string followed by parameter values.
  * `format-expr-list` -- intended to format a list of SQL expressions;
        returns a pair comprising: a sequence of SQL expressions (to be
        join with a delimiter) and a sequence of parameter values.
  * `set-dialect!` -- set the default dialect to be used for formatting.
  * `register-clause!` -- register a new statement/clause formatter.
  * `register-fn!` -- register a new function call (or special syntax)
        formatter.
  * `register-op!` -- register a new operator formatter."
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]))

;; default formatting for known clauses

(declare format-dsl)
(declare format-expr)
(declare format-expr-list)

;; dynamic dialect handling for formatting

(declare clause-format)
(def ^:private default-clause-order
  "The (default) order for known clauses. Can have items added and removed."
  [;; DDL comes first (these don't really have a precedence):
   :alter-table :add-column :drop-column :modify-column :rename-column
   :add-index :drop-index :rename-table
   :create-table :with-columns :create-view :drop-table
   ;; then SQL clauses in priority order:
   :nest :with :with-recursive :intersect :union :union-all :except :except-all
   :select :select-distinct :select-distinct-on
   :insert-into :update :delete :delete-from :truncate
   :columns :set :from :using
   :join :left-join :right-join :inner-join :outer-join :full-join
   :cross-join
   :where :group-by :having
   :window :partition-by
   :order-by :limit :offset :for :values
   :on-conflict :on-constraint :do-nothing :do-update-set
   :returning])

(defn- add-clause-before
  "Low-level helper just to insert a new clause."
  [order clause before]
  (if before
    (do
      (when-not (contains? (set order) before)
        (throw (ex-info (str "Unrecognized clause: " before)
                        {:known-clauses order})))
      (reduce (fn [v k]
                (if (= k before)
                  (conj v clause k)
                  (conj v k)))
              []
              order))
    (conj order clause)))

(def ^:private dialects
  {:ansi      {:quote #(str \" % \")}
   :sqlserver {:quote #(str \[ % \])}
   :mysql     {:quote #(str \` % \`)
               :clause-order-fn (fn [order]
                                  ;; :lock is like :for
                                  (swap! clause-format assoc :lock
                                         (get @clause-format :for))
                                  ;; MySQL :set has different priority
                                  ;; and :lock is between :for and :values
                                  (-> (filterv (complement #{:set}) order)
                                      (add-clause-before :set :where)
                                      (add-clause-before :lock :values)))}
   :oracle    {:quote #(str \" % \") :as false}})

; should become defonce
(def ^:private default-dialect (atom (:ansi dialects)))

(def ^:private ^:dynamic *dialect* nil)
;; nil would be a better default but that makes testing individual
;; functions harder than necessary:
(def ^:private ^:dynamic *clause-order* default-clause-order)
(def ^:private ^:dynamic *quoted* nil)
(def ^:private ^:dynamic *inline* nil)
(def ^:private ^:dynamic *params* nil)

;; clause helpers

;; String.toUpperCase() or `str/upper-case` for that matter converts the
;; string to uppercase for the DEFAULT LOCALE. Normally this does what you'd
;; expect but things like `inner join` get converted to `İNNER JOİN` (dot over
;; the I) when user locale is Turkish. This predictably has bad consequences
;; for people who like their SQL queries to work. The fix here is to use
;; String.toUpperCase(Locale/US) instead which always converts things the
;; way we'd expect.
;;
;; Use this instead of `str/upper-case` as it will always use Locale/US.
(def ^:private ^{:arglists '([s])} upper-case
  ;; TODO - not sure if there's a JavaScript equivalent here we should be using as well
  #?(:clj (fn [^String s] (.. s toString (toUpperCase (java.util.Locale/US))))
     :cljs str/upper-case))

(defn sql-kw
  "Given a keyword, return a SQL representation of it as a string.

  A `:kebab-case` keyword becomes a `KEBAB CASE` (uppercase) string
  with hyphens replaced by spaces, e.g., `:insert-into` => `INSERT INTO`.

  Any namespace qualifier is ignored."
  [k]
  (-> k (name) (upper-case)
      (as-> s (if (= "-" s) s (str/replace s "-" " ")))))

(defn- sym->kw
  "Given a symbol, produce a keyword, retaining the namespace
  qualifier, if any."
  [s]
  (if (symbol? s)
    (if-let [n (namespace s)]
      (keyword n (name s))
      (keyword (name s)))
    s))

(defn- kw->sym
  "Given a keyword, produce a symbol, retaining the namespace
  qualifier, if any."
  [k]
  (if (keyword? k)
    (if-let [n (namespace k)]
      (symbol n (name k))
      (symbol (name k)))
    k))

(defn- namespace-_ [x] (some-> (namespace x) (str/replace "-" "_")))
(defn- name-_      [x] (str/replace (name x) "-" "_"))

(defn format-entity
  "Given a simple SQL entity (a keyword or symbol -- or string),
  return the equivalent SQL fragment (as a string -- no parameters).

  Handles quoting, splitting at / or ., replacing - with _ etc."
  [x & [{:keys [aliased drop-ns]}]]
  (let [nn    (if (or *quoted* (string? x)) name               name-_)
        q     (if (or *quoted* (string? x)) (:quote *dialect*) identity)
        [t c] (if-let [n (when-not (or drop-ns (string? x))
                           (namespace-_ x))]
                [n (nn x)]
                (if aliased
                  [nil (nn x)]
                  (let [[t c] (str/split (nn x) #"\.")]
                    (if c [t c] [nil t]))))]
    (cond->> c
      (not= "*" c)
      (q)
      t
      (str (q t) "."))))

(defn- ->param [k]
  (with-meta (constantly k)
    {::wrapper
     (fn [fk _]
       (let [k (fk)]
         (if (contains? *params* k)
           (get *params* k)
           (throw (ex-info (str "missing parameter value for " k)
                           {:params (keys *params*)})))))}))

(defn- format-var [x & [opts]]
  (let [c (name-_ x)]
    (cond (= \% (first c))
          (let [[f & args] (str/split (subs c 1) #"\.")]
            ;; TODO: this does not quote arguments -- does that matter?
            [(str (upper-case f) "(" (str/join "," args) ")")])
          (= \? (first c))
          ["?" (->param (keyword (subs c 1)))]
          :else
          [(format-entity x opts)])))

(defn- format-entity-alias [x]
  (cond (sequential? x)
        (let [s     (first x)
              pair? (< 1 (count x))]
          (when (map? s)
            (throw (ex-info "selectable cannot be statement!"
                            {:selectable s})))
          (cond-> (format-entity s)
            pair?
            (str (if (and (contains? *dialect* :as) (not (:as *dialect*))) " " " AS ")
                 (format-entity (second x) {:aliased true}))))

        :else
        (format-entity x)))

(defn- format-selectable-dsl [x & [{:keys [as aliased] :as opts}]]
  (cond (map? x)
        (format-dsl x {:nested true})

        (sequential? x)
        (let [s     (first x)
              pair? (< 1 (count x))
              a     (second x)
              [sql & params] (if (map? s)
                               (format-dsl s {:nested true})
                               (format-expr s))
              [sql' & params'] (when pair?
                                 (if (sequential? a)
                                   (let [[sql params] (format-expr-list a {:aliased true})]
                                     (into [(str/join " " sql)] params))
                                   (format-selectable-dsl a {:aliased true})))]
          (-> [(cond-> sql
                 pair?
                 (str (if as
                        (if (and (contains? *dialect* :as)
                                 (not (:as *dialect*)))
                          " "
                          " AS ")
                        " ") sql'))]
              (into params)
              (into params')))

        (or (keyword? x) (symbol? x))
        (if aliased
          [(format-entity x opts)]
          (format-var x opts))

        (and aliased (string? x))
        [(format-entity x opts)]

        :else
        (format-expr x)))

;; primary clauses

(defn- format-on-set-op [k xs]
  (let [[sqls params]
        (reduce (fn [[sql params] [sql' & params']]
                  [(conj sql sql') (if params' (into params params') params)])
                [[] []]
                (map #(format-dsl % {:nested true}) xs))]
    (into [(str/join (str " " (sql-kw k) " ") sqls)] params)))

(defn format-expr-list
  "Given a sequence of expressions represented as data, return a pair
  where the first element is a sequence of SQL fragments and the second
  element is a sequence of parameters. The caller should join the SQL
  fragments with whatever appropriate delimiter is needed and then
  return a vector whose first element is the complete SQL string and
  whose subsequent elements are the parameters:

  (let [[sqls params] (format-expr-list data opts)]
    (into [(str/join delim sqls)] params))

  This is intended to be used when writing your own formatters to
  extend the DSL supported by HoneySQL."
  [exprs & [opts]]
  (reduce (fn [[sql params] [sql' & params']]
            [(conj sql sql') (if params' (into params params') params)])
          [[] []]
          (map #(format-expr % opts) exprs)))

(defn- format-columns [k xs]
  (let [[sqls params] (format-expr-list xs {:drop-ns (= :columns k)})]
    (into [(str "(" (str/join ", " sqls) ")")] params)))

(defn- format-selects-common [prefix as xs]
  (if (sequential? xs)
    (let [[sqls params]
          (reduce (fn [[sql params] [sql' & params']]
                    [(conj sql sql') (if params' (into params params') params)])
                  [[] []]
                  (map #(format-selectable-dsl % {:as as}) xs))]
      (into [(str prefix " " (str/join ", " sqls))] params))
    (let [[sql & params] (format-selectable-dsl xs {:as as})]
      (into [(str prefix " " sql)] params))))

(defn- format-selects [k xs]
  (format-selects-common
   (sql-kw k)
   (#{:select :select-distinct :from :window
      'select 'select-distinct 'from 'window}
    k)
   xs))

(defn- format-selects-on [k xs]
  (let [[on & cols] xs
        [sql & params]
        (format-expr (into [:distinct-on] on))
        [sql' & params']
        (format-selects-common
         (str (sql-kw :select) " " sql)
         true
         cols)]
    (-> [sql'] (into params) (into params'))))

(defn- format-with-part [x]
  (if (sequential? x)
    (let [[sql & params] (format-dsl (second x))]
      (into [(str (format-entity (first x)) " " sql)] params))
    [(format-entity x)]))

(defn- format-with [k xs]
  ;; TODO: a sequence of pairs -- X AS expr -- where X is either [entity expr]
  ;; or just entity, as far as I can tell...
  (let [[sqls params]
        (reduce (fn [[sql params] [sql' & params']]
                  [(conj sql sql') (if params' (into params params') params)])
                [[] []]
                (map (fn [[x expr]]
                       (let [[sql & params]   (format-with-part x)
                             [sql' & params'] (format-dsl expr)]
                         ;; according to docs, CTE should _always_ be wrapped:
                         (cond-> [(str sql " AS " (str "(" sql' ")"))]
                           params  (into params)
                           params' (into params'))))
                     xs))]
    (into [(str (sql-kw k) " " (str/join ", " sqls))] params)))

(defn- format-selector [k xs]
  (format-selects k [xs]))

(defn- format-insert [k table]
  (if (sequential? table)
    (cond (map? (second table))
          (let [[table statement] table
                [table cols]
                (if (and (sequential? table) (sequential? (second table)))
                  table
                  [table])
                [sql & params] (format-dsl statement)]
            (into [(str (sql-kw k) " " (format-entity-alias table)
                        " "
                        (when (seq cols)
                          (str "("
                               (str/join ", " (map #'format-entity-alias cols))
                               ") "))
                        sql)]
                  params))
          (sequential? (second table))
          (let [[table cols] table]
            [(str (sql-kw k) " " (format-entity-alias table)
                  " ("
                  (str/join ", " (map #'format-entity-alias cols))
                  ")")])
          :else
          [(str (sql-kw k) " " (format-entity-alias table))])
    [(str (sql-kw k) " " (format-entity-alias table))]))

(defn- format-join [k clauses]
  (let [[sqls params]
        (reduce (fn [[sqls params] [j e]]
                  (let [sqls (conj sqls
                                   (sql-kw (if (= :join k) :inner-join k))
                                   (format-entity-alias j))]
                    (if (and (sequential? e) (= :using (first e)))
                      [(conj sqls
                             "USING"
                             (str "("
                                  (str/join ", " (map #'format-entity-alias (rest e)))
                                  ")"))
                       params]
                      (let [[sql & params'] (when e (format-expr e))]
                        [(cond-> sqls e (conj "ON" sql))
                         (into params params')]))))
                [[] []]
                (partition 2 clauses))]
    (into [(str/join " " sqls)] params)))

(defn- format-on-expr [k e]
  (if (or (not (sequential? e)) (seq e))
    (let [[sql & params] (format-expr e)]
      (into [(str (sql-kw k) " " sql)] params))
    []))

(defn- format-group-by [k xs]
  (let [[sqls params] (format-expr-list xs)]
    (into [(str (sql-kw k) " " (str/join ", " sqls))] params)))

(defn- format-order-by [k xs]
  (let [dirs (map #(when (sequential? %) (second %)) xs)
        [sqls params]
        (format-expr-list (map #(if (sequential? %) (first %) %) xs))]
    (into [(str (sql-kw k) " "
                (str/join ", " (map (fn [sql dir]
                                      (str sql " " (sql-kw (or dir :asc))))
                                    sqls
                                    dirs)))] params)))

(defn- format-lock-strength [k xs]
  (let [[strength tables nowait] (if (sequential? xs) xs [xs])]
    [(str (sql-kw k) " " (sql-kw strength)
          (when tables
            (str
              (cond (and (keyword? tables)
                         (#{:nowait :skip-locked :wait} tables))
                    (str " " (sql-kw tables))
                    (and (symbol? tables)
                         ('#{nowait skip-locked wait} tables))
                    (str " " (sql-kw tables))
                    (sequential? tables)
                    (str " OF "
                         (str/join ", " (map #'format-entity tables)))
                    :else
                    (str " OF " (format-entity tables)))
              (when nowait
                (str " " (sql-kw nowait))))))]))

(defn- format-values [k xs]
  (cond (sequential? (first xs))
        ;; [[1 2 3] [4 5 6]]
        (let [n-1 (map count xs)
              ;; issue #291: ensure all value sequences are the same length
              xs' (if (apply = n-1)
                    xs
                    (let [n-n (apply max n-1)]
                      (map (fn [x] (take n-n (concat x (repeat nil)))) xs)))
              [sqls params]
              (reduce (fn [[sql params] [sqls' params']]
                        [(conj sql (str "(" (str/join ", " sqls') ")"))
                         (into params params')])
                      [[] []]
                      (map #'format-expr-list xs'))]
          (into [(str (sql-kw k) " " (str/join ", " sqls))] params))

        (map? (first xs))
        ;; [{:a 1 :b 2 :c 3}]
        (let [cols-1 (keys (first xs))
              ;; issue #291: check for all keys in all maps but still
              ;; use the keys from the first map if they match so that
              ;; users can rely on the key ordering if they want to,
              ;; e.g., see test that uses array-map for the first row
              cols-n (into #{} (mapcat keys) xs)
              cols   (if (= (set cols-1) cols-n) cols-1 cols-n)
              [sqls params]
              (reduce (fn [[sql params] [sqls' params']]
                        [(conj sql (str "(" (str/join ", " sqls') ")"))
                         (if params' (into params params') params')])
                      [[] []]
                      (map (fn [m]
                             (format-expr-list (map #(get m %) cols)))
                           xs))]
          (into [(str "("
                      (str/join ", "
                                (map #(format-entity % {:drop-ns true}) cols))
                      ") "
                      (sql-kw k)
                      " "
                      (str/join ", " sqls))]
                params))

        :else
        (throw (ex-info ":values expects sequences or maps"
                        {:first (first xs)}))))

(comment
  (into #{} (mapcat keys) [{:a 1 :b 2} {:b 3 :c 4}])
  ,)

(defn- format-set-exprs [k xs]
  (let [[sqls params]
        (reduce-kv (fn [[sql params] v e]
                     (let [[sql' & params'] (format-expr e)]
                       [(conj sql (str (format-entity v) " = " sql'))
                        (if params' (into params params') params)]))
                   [[] []]
                   xs)]
    (into [(str (sql-kw k) " " (str/join ", " sqls))] params)))

(defn- format-on-conflict [k x]
  (cond (or (keyword? x) (symbol? x))
        [(str (sql-kw k) " (" (format-entity x) ")")]
        (map? x)
        (let [[sql & params] (format-dsl x)]
          (into [(str (sql-kw k) " " sql)] params))
        (and (sequential? x)
             (or (keyword? (first x)) (symbol? (first x)))
             (map? (second x)))
        (let [[sql & params] (format-dsl (second x))]
          (into [(str (sql-kw k)
                      " (" (format-entity (first x)) ") "
                      sql)]
                params))
        :else
        (throw (ex-info "unsupported :on-conflict format"
                        {:clause x}))))
(comment
  keyword/symbol -> e = excluded.e
  [k/s] -> join , e = excluded.e
  {e v} -> join , e = v
  {:fields f :where w} -> join , e = excluded.e (from f) where w

  ,)
(defn- format-do-update-set [k x]
  (if (map? x)
    (if (and (or (contains? x :fields) (contains? x 'fields))
             (or (contains? x :where)  (contains? x 'where)))
      (let [sets (str/join ", "
                           (map (fn [e]
                                  (let [e (format-entity e {:drop-ns true})]
                                    (str e " = EXCLUDED." e)))
                                (or (:fields x)
                                    ('fields x))))
            [sql & params] (format-dsl {:where
                                        (or (:where x)
                                            ('where x))})]
        (into [(str (sql-kw k) " " sets " " sql)] params))
      (format-set-exprs k x))
    (let [e (format-entity x {:drop-ns true})]
      [(str (sql-kw k) " " e " = EXCLUDED." e)])))

(defn- format-simple-clause [c]
  (binding [*inline* true]
    (let [[x & y] (format-dsl c)]
      (when (seq y)
        (throw (ex-info "column/index operations must be simple clauses"
                        {:clause c :params y})))
      x)))

(defn- format-alter-table [k x]
  (if (sequential? x)
    [(str (sql-kw k) " " (format-entity (first x))
          (when-let [clauses (next x)]
            (str " " (str/join ", " (map #'format-simple-clause clauses)))))]
    [(str (sql-kw k) " " (format-entity x))]))

(defn- format-create-table [k table]
  (let [[table if-not-exists] (if (sequential? table) table [table])]
   [(str (sql-kw k) " "
         (when if-not-exists (str (sql-kw :if-not-exists) " "))
         (format-entity table))]))

(defn- format-create-view [k x]
  [(str (sql-kw k) " " (format-entity x) " AS")])

(defn- format-drop-table
  [k params]
  (let [tables (if (sequential? params) params [params])
        [if-exists & tables] (if (#{:if-exists 'if-exists} (first tables)) tables (cons nil tables))]
   [(str (sql-kw k) " "
         (when if-exists (str (sql-kw :if-exists) " "))
         (str/join ", " (map #'format-entity tables)))]))

(defn- format-simple-expr [e]
  (binding [*inline* true]
    (let [[x & y] (format-expr e)]
      (when (seq y)
        (throw (ex-info "column elements must be simple expressions"
                        {:expr e :params y})))
      x)))

(defn- format-single-column [xs]
  (str/join " " (let [[id & spec] (map #'format-simple-expr xs)]
                  (cons id (map upper-case spec)))))

(defn- format-table-columns [k xs]
  [(str "("
        (str/join ", " (map #'format-single-column xs))
        ")")])

(defn- format-add-item [k spec]
  [(str (sql-kw k) " " (format-single-column spec))])

(defn- format-rename-item [k [x y]]
  [(str (sql-kw k) " " (format-entity x) " TO " (format-entity y))])

(def ^:private base-clause-order
  "The (base) order for known clauses. Can have items added and removed.

  This is the 'pre-dialect' ordering."
  (atom default-clause-order))

(def ^:private current-clause-order
  "The (current) order for known clauses. Can have items added and removed.

  This is the 'post-dialect` ordering when a new default dialect is set."
  (atom default-clause-order))

(def ^:private clause-format
  "The (default) behavior for each known clause. Can also have items added
  and removed."
  (atom {:alter-table     #'format-alter-table
         :add-column      #'format-add-item
         :drop-column     #'format-selector
         :modify-column   #'format-add-item
         :rename-column   #'format-rename-item
         ;; so :add-index works with both [:index] and [:unique]
         :add-index       (fn [_ x] (format-on-expr :add x))
         :drop-index      #'format-selector
         :rename-table    (fn [_ x] (format-selector :rename-to x))
         :create-table    #'format-create-table
         :with-columns    #'format-table-columns
         :create-view     #'format-create-view
         :drop-table      #'format-drop-table
         :nest            (fn [_ x] (format-expr x))
         :with            #'format-with
         :with-recursive  #'format-with
         :intersect       #'format-on-set-op
         :union           #'format-on-set-op
         :union-all       #'format-on-set-op
         :except          #'format-on-set-op
         :except-all      #'format-on-set-op
         :select          #'format-selects
         :select-distinct #'format-selects
         :select-distinct-on #'format-selects-on
         :insert-into     #'format-insert
         :update          #'format-selector
         :delete          #'format-selects
         :delete-from     #'format-selector
         :truncate        #'format-selector
         :columns         #'format-columns
         :set             #'format-set-exprs
         :from            #'format-selects
         :using           #'format-selects
         :join            #'format-join
         :left-join       #'format-join
         :right-join      #'format-join
         :inner-join      #'format-join
         :outer-join      #'format-join
         :full-join       #'format-join
         :cross-join      #'format-selects
         :where           #'format-on-expr
         :group-by        #'format-group-by
         :having          #'format-on-expr
         :window          #'format-selector
         :partition-by    #'format-selects
         :order-by        #'format-order-by
         :limit           #'format-on-expr
         :offset          #'format-on-expr
         :for             #'format-lock-strength
         :values          #'format-values
         :on-conflict     #'format-on-conflict
         :on-constraint   #'format-selector
         :do-nothing      (fn [k _] (vector (sql-kw k)))
         :do-update-set   #'format-do-update-set
         :returning       #'format-selects}))

(assert (= (set @base-clause-order)
           (set @current-clause-order)
           (set (keys @clause-format))))

(defn format-dsl
  "Given a hash map representing a SQL statement and a hash map
  of options, return a vector containing a string -- the formatted
  SQL statement -- followed by any parameter values that SQL needs.

  This is intended to be used when writing your own formatters to
  extend the DSL supported by HoneySQL."
  [statement-map & [{:keys [aliased nested pretty]}]]
  (let [[sqls params leftover]
        (reduce (fn [[sql params leftover] k]
                  (if-let [xs (or (k statement-map)
                                  (let [s (kw->sym k)]
                                    (get statement-map s)))]
                    (let [formatter (k @clause-format)
                          [sql' & params'] (formatter k xs)]
                      [(conj sql sql')
                       (if params' (into params params') params)
                       (dissoc leftover k (kw->sym k))])
                    [sql params leftover]))
                [[] [] statement-map]
                *clause-order*)]
    (if (seq leftover)
      (throw (ex-info (str "Unknown SQL clauses: "
                            (str/join ", " (keys leftover)))
                      leftover))
      (into [(cond-> (str/join (if pretty "\n" " ") (filter seq sqls))
               pretty
               (as-> s (str "\n" s "\n"))
               (and nested (not aliased))
               (as-> s (str "(" s ")")))] params))))

(def ^:private infix-aliases
  "Provided for backward compatibility with earlier HoneySQL versions."
  {:is :=
   :is-not :<>
   :not= :<>
   :!= :<>
   :regex :regexp})

(def ^:private infix-ops
  (-> #{"mod" "and" "or" "xor" "<>" "<=" ">=" "||"
        "like" "not-like" "regexp"
        "ilike" "not-ilike" "similar-to" "not-similar-to"
        "is" "is-not" "not=" "!=" "regex"}
      (into (map str "+-*%|&^=<>"))
      (into (keys infix-aliases))
      (into (vals infix-aliases))
      (->> (into #{} (map keyword)))
      (conj :/) ; because (keyword "/") does not work in cljs
      (atom)))

(def ^:private op-ignore-nil (atom #{:and :or}))
(def ^:private op-variadic   (atom #{:and :or :+ :* :||}))

(defn- sqlize-value [x]
  (cond
    (nil? x)     "NULL"
    (string? x)  (str \' (str/replace x "'" "''") \')
    (symbol? x)  (sql-kw x)
    (keyword? x) (sql-kw x)
    :else        (str x)))

(defn- unwrap [x opts]
  (if-let [m (meta x)]
    (if-let [f (::wrapper m)]
      (f x opts)
      x)
    x))

(defn- format-in [in [x y]]
  (let [[sql-x & params-x] (format-expr x {:nested true})
        [sql-y & params-y] (format-expr y {:nested true})
        values             (unwrap (first params-y) {})]
    (if (and (= "?" sql-y) (= 1 (count params-y)) (coll? values))
      (let [sql (str "(" (str/join ", " (repeat (count values) "?")) ")")]
        (-> [(str sql-x " " (sql-kw in) " " sql)]
            (into params-x)
            (into values)))
      (-> [(str sql-x " " (sql-kw in) " " sql-y)]
          (into params-x)
          (into params-y)))))

(defn- function-0 [k xs]
  [(str (sql-kw k)
        (when (seq xs)
          (str "(" (str/join ", " (map #'format-simple-expr xs)) ")")))])

(defn- function-1 [k xs]
  [(str (sql-kw k)
        (when (seq xs)
          (str " " (format-simple-expr (first xs))
               (when-let [args (next xs)]
                 (str "(" (str/join ", " (map #'format-simple-expr args)) ")")))))])

(defn- function-1-opt [k xs]
  [(str (sql-kw k)
        (when (seq xs)
          (str (when-let [e (first xs)]
                 (str " " (format-simple-expr e)))
               (when-let [args (next xs)]
                 (str "(" (str/join ", " (map #'format-simple-expr args)) ")")))))])

(def ^:private special-syntax
  (atom
   {;; these "functions" are mostly used in column
    ;; descriptions so they generally have one of two forms:
    ;; function-0 - with zero arguments, renders as a keyword,
    ;;     otherwise renders as a function call
    ;; function-1 - with zero arguments, renders as a keyword,
    ;;     with one argument, as a keyword followed by an entity,
    ;;     otherwise renders as a keyword followed by a function
    ;;     call using the first entity as the function
    ;; function-1-opt - like function-1 except if the first
    ;;     argument is nil, it is omitted
    :constraint  #'function-1
    :default     #'function-1
    :foreign-key #'function-0
    :index       #'function-1-opt
    :primary-key #'function-0
    :references  #'function-1
    :unique      #'function-1-opt
    :array
    (fn [_ [arr]]
      (let [[sqls params] (format-expr-list arr)]
        (into [(str "ARRAY[" (str/join ", " sqls) "]")] params)))
    :between
    (fn [_ [x a b]]
      (let [[sql-x & params-x] (format-expr x {:nested true})
            [sql-a & params-a] (format-expr a {:nested true})
            [sql-b & params-b] (format-expr b {:nested true})]
        (-> [(str sql-x " BETWEEN " sql-a " AND " sql-b)]
            (into params-x)
            (into params-a)
            (into params-b))))
    :case
    (fn [_ clauses]
      (let [[sqls params]
            (reduce (fn [[sqls params] [condition value]]
                      (let [[sqlc & paramsc] (when-not (= :else condition)
                                               (format-expr condition))
                            [sqlv & paramsv] (format-expr value)]
                        [(if (or (= :else condition)
                                 (= 'else condition))
                           (conj sqls (sql-kw :else) sqlv)
                           (conj sqls (sql-kw :when) sqlc (sql-kw :then) sqlv))
                         (-> params (into paramsc) (into paramsv))]))
                    [[] []]
                    (partition 2 clauses))]
        (into [(str (sql-kw :case) " "
                    (str/join " " sqls)
                    " " (sql-kw :end))]
              params)))
    :cast
    (fn [_ [x type]]
      (let [[sql & params]   (format-expr x)
            [sql' & params'] (format-expr type)]
        (-> [(str "CAST(" sql " AS " sql' ")")]
            (into params)
            (into params'))))
    :composite
    (fn [_ [& args]]
      (let [[sqls params] (format-expr-list args)]
        (into [(str "(" (str/join ", " sqls) ")")] params)))
    :inline
    (fn [_ [x]]
      (if (sequential? x)
        [(str/join " " (map #'sqlize-value x))]
        [(sqlize-value x)]))
    :interval
    (fn [_ [n units]]
      (let [[sql & params] (format-expr n)]
        (into [(str "INTERVAL " sql " " (sql-kw units))] params)))
    :lift
    (fn [_ [x]]
      ["?" (with-meta (constantly x)
             {::wrapper (fn [fx _] (fx))})])
    :nest
    (fn [_ [x]]
      (format-expr x {:nested true}))
    :not
    (fn [_ [x]]
      (let [[sql & params] (format-expr x)]
        (into [(str "NOT " sql)] params)))
    :over
    (fn [_ [& args]]
      (let [[sqls params]
            (reduce (fn [[sqls params] [e p a]]
                      (let [[sql-e & params-e] (format-expr e)
                            [sql-p & params-p] (if (or (nil? p) (map? p))
                                                 (format-dsl p {:nested true})
                                                 [(format-entity p)])]
                        [(conj sqls (str sql-e " OVER " sql-p
                                         (when a (str " AS " (format-entity a)))))
                         (-> params (into params-e) (into params-p))]))
                    [[] []]
                    args)]
        (into [(str/join ", " sqls)] params)))
    :param
    (fn [_ [k]]
      ["?" (->param k)])
    :raw
    (fn [_ [s]]
      (if (sequential? s)
        (let [[sqls params]
              (reduce (fn [[sqls params] s]
                        (if (sequential? s)
                          (let [[sql & params'] (format-expr s)]
                            [(conj sqls sql)
                             (into params params')])
                          [(conj sqls s) params]))
                      [[] []]
                      s)]
          (into [(str/join sqls)] params))
        [s]))}))

(defn format-expr
  "Given a data structure that represents a SQL expression and a hash
  map of options, return a vector containing a string -- the formatted
  SQL statement -- followed by any parameter values that SQL needs.

  This is intended to be used when writing your own formatters to
  extend the DSL supported by HoneySQL."
  [expr & [{:keys [nested] :as opts}]]
  (cond (or (keyword? expr) (symbol? expr))
        (format-var expr opts)

        (map? expr)
        (format-dsl expr (assoc opts :nested true))

        (sequential? expr)
        (let [op (sym->kw (first expr))]
          (if (keyword? op)
            (cond (contains? @infix-ops op)
                  (if (contains? @op-variadic op) ; no aliases here, no special semantics
                    (let [x (if (contains? @op-ignore-nil op)
                              (remove nil? expr)
                              expr)
                          [sqls params]
                          (reduce (fn [[sql params] [sql' & params']]
                                    [(conj sql sql')
                                     (if params' (into params params') params)])
                                  [[] []]
                                  (map #(format-expr % {:nested true})
                                       (rest x)))]
                      (into [(cond-> (str/join (str " " (sql-kw op) " ") sqls)
                               nested
                               (as-> s (str "(" s ")")))]
                            params))
                    (let [[_ a b & y] expr
                          _           (when (seq y)
                                        (throw (ex-info (str "only binary "
                                                             op
                                                             " is supported")
                                                        {:expr expr})))
                          [s1 & p1]   (format-expr a {:nested true})
                          [s2 & p2]   (format-expr b {:nested true})
                          op          (get infix-aliases op op)]
                        (if (and (#{:= :<>} op) (or (nil? a) (nil? b)))
                          (-> (str (if (nil? a)
                                     (if (nil? b) "NULL" s2)
                                     s1)
                                   (if (= := op) " IS NULL" " IS NOT NULL"))
                              (cond-> nested
                                (as-> s (str "(" s ")")))
                              (vector))
                          (-> (str s1 " " (sql-kw op) " " s2)
                              (cond-> nested
                                (as-> s (str "(" s ")")))
                              (vector)
                              (into p1)
                              (into p2)))))
                  (contains? #{:in :not-in} op)
                  (let [[sql & params] (format-in op (rest expr))]
                    (into [(if nested (str "(" sql ")") sql)] params))
                  (contains? @special-syntax op)
                  (let [formatter (get @special-syntax op)]
                    (formatter op (rest expr)))
                  :else
                  (let [args          (rest expr)
                        [sqls params] (format-expr-list args)]
                    (into [(str (sql-kw op)
                                (if (and (= 1 (count args))
                                         (map? (first args))
                                         (= 1 (count sqls)))
                                  (str " " (first sqls))
                                  (str "(" (str/join ", " sqls) ")")))]
                          params)))
            (let [[sqls params] (format-expr-list expr)]
              (into [(str "(" (str/join ", " sqls) ")")] params))))

        (boolean? expr)
        [(upper-case (str expr))]

        (nil? expr)
        ["NULL"]

        :else
        (if *inline*
          [(sqlize-value expr)]
          ["?" expr])))

(defn- check-dialect [dialect]
  (when-not (contains? dialects dialect)
    (throw (ex-info (str "Invalid dialect: " dialect)
                    {:valid-dialects (vec (sort (keys dialects)))})))
  dialect)

(defn format
  "Turn the data DSL into a vector containing a SQL string followed by
  any parameter values that were encountered in the DSL structure.

  This is the primary API for HoneySQL and handles dialects, quoting,
  and named parameters."
  ([data] (format data {}))
  ([data opts]
   (let [dialect? (contains? opts :dialect)
         dialect  (when dialect? (get dialects (check-dialect (:dialect opts))))]
     (binding [*dialect* (if dialect? dialect @default-dialect)
               *clause-order* (if dialect?
                                (if-let [f (:clause-order-fn dialect)]
                                  (f @base-clause-order)
                                  @current-clause-order)
                                @current-clause-order)
               *inline*  (when (contains? opts :inline)
                           (:inline opts))
               *quoted*  (if (contains? opts :quoted)
                           (:quoted opts)
                           dialect?)
               *params* (:params opts)]
       (mapv #(unwrap % opts) (format-dsl data opts))))))

(defn set-dialect!
  "Set the default dialect for formatting.

  Can be: `:ansi` (the default), `:mysql`, `:oracle`, or `:sqlserver`.

  Dialects are always applied to the base order to create the current order."
  [dialect]
  (reset! default-dialect (get dialects (check-dialect dialect)))
  (when-let [f (:clause-order-fn @default-dialect)]
    (reset! current-clause-order (f @base-clause-order))))

(defn register-clause!
  "Register a new clause formatter. If `before` is `nil`, the clause is
  added to the end of the list of known clauses, otherwise it is inserted
  immediately prior to that clause.

  New clauses are registered in the base order and the current order so
  that any dialect selections are able to include them while still working
  predictably from the base order. Caveat: that means if you register a new
  clause `before` a clause that is ordered differently in different
  dialects, your new clause may also end up in a different place. The
  only clause so far where that would matter is `:set` which differs in
  MySQL."
  [clause formatter before]
  (let [clause (sym->kw clause)
        before (sym->kw before)]
    (assert (keyword? clause))
    (let [k (sym->kw formatter)
          f (if (keyword? k)
              (get @clause-format k)
              formatter)]
      (when-not (and f (fn? f))
        (throw (ex-info "The formatter must be a function or existing clause"
                        {:type (type formatter)})))
      (swap! base-clause-order add-clause-before clause before)
      (swap! current-clause-order add-clause-before clause before)
      (swap! clause-format assoc clause f))))

(defn register-fn!
  "Register a new function (as special syntax). The `formatter` is either
  a keyword, meaning that this new function should use the same syntax as
  an existing function, or a function of two arguments that generates a
  SQL string and parameters (as a vector). The two arguments are the name
  of the function (as a keyword) and a sequence of the arguments from the
  DSL."
  [function formatter]
  (let [function (sym->kw function)]
    (assert (keyword? function))
    (let [k (sym->kw formatter)
          f (if (keyword? k)
              (get @special-syntax k)
              formatter)]
      (when-not (and f (fn? f))
        (throw (ex-info "The formatter must be a function or existing fn name"
                        {:type (type formatter)})))
      (swap! special-syntax assoc function f))))

(defn register-op!
  "Register a new infix operator. Operators can be defined to be variadic (the
  default is that they are binary) and may choose to ignore `nil` arguments
  (this can make it easier to programmatically construct the DSL)."
  [op & {:keys [variadic ignore-nil]}]
  (let [op (sym->kw op)]
    (assert (keyword? op))
    (swap! infix-ops conj op)
    (when variadic
      (swap! op-variadic conj op))
    (when ignore-nil
      (swap! op-ignore-nil conj op))))

(comment
  (format {:truncate :foo})
  (format-expr [:= :id 1])
  (format-expr [:+ :id 1])
  (format-expr [:+ 1 [:+ 1 :quux]])
  (format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])
  (format-expr :id)
  (format-expr 1)
  (format {:select [:a [:b :c] [[:d :e]] [[:f :g] :h]]})
  (format {:select [[[:d :e]] :a [:b :c]]})
  (format-on-expr :where [:= :id 1])
  (format-dsl {:select [:*] :from [:table] :where [:= :id 1]})
  (format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]} {})
  (format {:select [:*] :from [:table] :group-by [:foo :bar]} {})
  (format {:select [:*] :from [:table] :group-by [[:date :bar]]} {})
  (format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]} {})
  (format {:select [:*] :from [:table]
           :order-by [[[:date :expiry] :desc] :bar]} {})
  (println (format {:select [:*] :from [:table]
                    :order-by [[[:date :expiry] :desc] :bar]} {:pretty true}))
  (format {:select [:*] :from [:table]
           :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]} {})
  (format-expr [:interval 30 :days])
  (format {:select [:*] :from [:table]
           :where [:= :id (int 1)]} {:dialect :mysql})
  (map fn? (format {:select [:*] :from [:table]
                    :where [:= :id (with-meta (constantly 42) {:foo true})]}
                   {:dialect :mysql}))
  (println (format {:select [:*] :from [:table]
                    :where [:in :id [1 2 3 4]]} {:pretty true}))
  (println (format {:select [:*] :from [:table]
                    :where [:and [:in :id [1 [:param :foo]]]
                            [:= :bar [:param :quux]]]}
                   {:params {:foo 42 :quux 13}
                    :pretty true}))
  ;; while working on the docs
  (require '[honey.sql :as sql])
  (sql/format-expr [:array (range 5)])
  (sql/format {:where [:and [:= :id 42] [:= :type "match"]]})
  (sql/format {:where [:and [:= :type "match"] (when false [:in :status [1 5]])]})
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]})
  (sql/format {:select [:t/id [:name :item]], :from [[:table :t]], :where [:= :id 1]})
  (sql/format '{select [t/id [name item]], from [[table t]], where [= id 1]})
  (sql/format '{select * from table where (= id 1)})
  (require '[honey.sql.helpers :refer [select from where]])
  (-> (select :t/id [:name :item])
      (from [:table :t])
      (where [:= :id 1])
      (sql/format))
  (-> (select :t/id)
      (from [:table :t])
      (where [:= :id 1])
      (select [:name :item])
      (sql/format))
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})
  (sql/format {:select [:foo/bar] :from [:q-u-u-x]} {:quoted true})
  (sql/format {:select ["foo/bar"] :from [:q-u-u-x]} {:quoted true})
  (sql/format-expr [:primary-key])
  (sql/register-op! 'y)
  (sql/format {:where '[y 2 3]})
  (sql/register-op! :<=> :variadic true :ignore-nil true)
  ;; and then use the new operator:
  (sql/format {:select [:*], :from [:table], :where [:<=> nil :x 42]})
  (sql/register-fn! :foo (fn [f args] ["FOO(?)" (first args)]))
  (sql/format {:select [:*], :from [:table], :where [:foo 1 2 3]})
  (defn- foo-formatter [f [x]]
    (let [[sql & params] (sql/format-expr x)]
      (into [(str (sql/sql-kw f) "(" sql ")")] params)))

  (sql/register-fn! :foo foo-formatter)

  (sql/format {:select [:*], :from [:table], :where [:foo [:+ :a 1]]})
  ,)
