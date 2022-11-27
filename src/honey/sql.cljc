;; copyright (c) 2020-2022 sean corfield, all rights reserved

(ns honey.sql
  "Primary API for HoneySQL 2.x.

  This includes the `format` function -- the primary entry point -- as well
  as several public formatters that are intended to help users extend the
  supported syntax.

  In addition, functions to extend HoneySQL are also provided here:
  * `clause-order` -- returns the current clause priority ordering;
        intended as aid when registering new clauses.
  * `format-dsl` -- intended to format SQL statements; returns a vector
        containing a SQL string followed by parameter values.
  * `format-entity` -- intended to format SQL entities; returns a string
        representing the SQL entity.
  * `-format-expr` -- intended to format SQL expressions; returns a vector
        containing a SQL string followed by parameter values.
  * `format-expr-list` -- intended to format a list of SQL expressions;
        returns a pair comprising: a sequence of SQL expressions (to be
        join with a delimiter) and a sequence of parameter values.
  * `register-clause!` -- register a new statement/clause formatter.
  * `register-fn!` -- register a new function call (or special syntax)
        formatter.
  * `register-op!` -- register a new operator formatter.
  * `set-dialect!` -- set the default dialect to be used for formatting,
        and optionally set a global `:quoted` option.
  * `sql-kw` -- turns a Clojure keyword (or symbol) into SQL code (makes
        it uppercase and replaces - with space). "
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]
            [honey.sql.protocols :as p])
  #?(:clj (:import [java.lang StringBuffer])
     :cljs (:import [goog.string StringBuffer])))

;; default formatting for known clauses

(declare format-dsl)
(declare -format-expr)
(declare format-expr-list)

;; dynamic dialect handling for formatting

(declare clause-format)
(def ^:private default-clause-order
  "The (default) order for known clauses. Can have items added and removed."
  [;; DDL comes first (these don't really have a precedence):
   :alter-table :add-column :drop-column
   :alter-column :modify-column :rename-column
   :add-index :drop-index :rename-table
   :create-table :create-table-as :with-columns
   :create-view :create-materialized-view :create-extension
   :drop-table :drop-view :drop-materialized-view :drop-extension
   :refresh-materialized-view
   ;; then SQL clauses in priority order:
   :raw :nest :with :with-recursive :intersect :union :union-all :except :except-all
   :table
   :select :select-distinct :select-distinct-on :select-top :select-distinct-top
   :into :bulk-collect-into
   :insert-into :update :delete :delete-from :truncate
   :columns :set :from :using
   :join-by
   :join :left-join :right-join :inner-join :outer-join :full-join
   :cross-join
   :where :group-by :having
   :window :partition-by
   :order-by :limit :offset :fetch :for :lock :values
   :on-conflict :on-constraint :do-nothing :do-update-set :on-duplicate-key-update
   :returning
   :with-data])

(defn add-clause-before
  "Low-level helper just to insert a new clause.

  If the clause is already in the list, this moves it to the end."
  [order clause before]
  (let [clauses (set order)
        order   (if (contains? clauses clause)
                  (filterv #(not= % clause) order)
                  order)]
    (if before
      (do
        (when-not (contains? clauses before)
          (throw (ex-info (str "Unrecognized clause: " before)
                          {:known-clauses order})))
        (reduce (fn [v k]
                  (if (= k before)
                    (conj v clause k)
                    (conj v k)))
                []
                order))
      (conj order clause))))

(defn strop
  "Escape any embedded closing strop characters."
  [s x e]
  (str s (str/replace x (str e) (str e e)) e))

(declare register-clause!)

(def ^:private dialects
  (atom
   (reduce-kv (fn [m k v]
                (assoc m k (assoc v :dialect k)))
              {}
              {:ansi      {:quote #(strop \" % \")}
               :sqlserver {:quote #(strop \[ % \])}
               :mysql     {:quote #(strop \` % \`)
                           :clause-order-fn
                           #(do
                              ;; side-effect: updates global clauses...
                              (register-clause! :replace-into :insert-into :insert-into)
                              (-> %
                                  (add-clause-before :set :where)
                                  ;; ...but not in-flight clauses:
                                  (add-clause-before :replace-into :insert-into)))}
               :oracle    {:quote #(strop \" % \") :as false}})))

; should become defonce
(def ^:private default-dialect (atom (:ansi @dialects)))
(def ^:private default-quoted (atom nil))
(def ^:private default-quoted-snake (atom nil))
(def ^:private default-inline (atom nil))
(def ^:private default-checking (atom :none))
(def ^:private default-marker-formatter (atom "?"))
(def ^:private default-marker-init-index (atom 1))

(def ^:private ^:dynamic *dialect* nil)
;; nil would be a better default but that makes testing individual
;; functions harder than necessary:
(def ^:private ^:dynamic *marker-formatter* @default-marker-formatter)
(def ^:private ^:dynamic *marker-init-index* @default-marker-init-index)
(def ^:private ^:dynamic *clause-order* default-clause-order)
(def ^:private ^:dynamic *quoted* @default-quoted)
(def ^:private ^:dynamic *quoted-snake* @default-quoted-snake)
(def ^:private ^:dynamic *inline* @default-inline)
(def ^:private ^:dynamic *params* nil)
(def ^:private ^:dynamic *values-default-columns* nil)
;; there is no way, currently, to enable suspicious characters
;; in entities; if someone complains about this check, an option
;; can be added to format to turn this on:
(def ^:private ^:dynamic *allow-suspicious-entities* false)
;; "linting" mode (:none, :basic, :strict):
(def ^:private ^:dynamic *checking* @default-checking)
;; the current DSL hash map being formatted (for contains-clause?):
(def ^:private ^:dynamic *dsl* nil)
;; caching data to detect expressions that cannot be cached:
(def ^:private ^:dynamic *caching* nil)

;; clause helpers

(defn contains-clause?
  "Returns true if the current DSL expression being formatted
  contains the specified clause (as a keyword or symbol)."
  [clause]
  (or (contains? *dsl* clause)
      (contains? *dsl*
                 (if (keyword? clause)
                   (symbol (name clause))
                   (keyword (name clause))))))

(defn- mysql?
  "Helper to detect if MySQL is the current dialect."
  []
  (= :mysql (:dialect *dialect*)))

(defn- sql-server?
  "Helper to detect if SQL Server is the current dialect."
  []
  (= :sqlserver (:dialect *dialect*)))

;; String.toUpperCase() or `str/upper-case` for that matter converts the
;; string to uppercase for the DEFAULT LOCALE. Normally this does what you'd
;; expect but things like `inner join` get converted to `İNNER JOİN` (dot over
;; the I) when user locale is Turkish. This predictably has bad consequences
;; for people who like their SQL queries to work. The fix here is to use
;; String.toUpperCase(Locale/US) instead which always converts things the
;; way we'd expect.
;;
;; Use this instead of `str/upper-case` as it will always use Locale/US.
#?(:clj
   (defn upper-case
     "Upper-case a string in Locale/US to avoid locale-specific capitalization."
     [^String s]
     (.. s toString (toUpperCase (java.util.Locale/US))))
   ;; TODO - not sure if there's a JavaScript equivalent here we should be using as well
   :cljs
   (defn upper-case
     "In ClojureScript, just an alias for cljs.string/upper-case."
     [s]
     (str/upper-case s)))

(defn- dehyphen
  "Replace _embedded_ hyphens with spaces.

  Hyphens at the start or end of a string should not be touched."
  [s]
  (str/replace s #"(\w)-(?=\w)" "$1 "))

(defn- namespace-_
  "Return the namespace portion of a symbol, with dashes converted."
  [x]
  (try
    (some-> (namespace x) (str/replace "-" "_"))
    (catch #?(:clj Throwable :cljs :default) t
      (throw (ex-info (str "expected symbol, found: "
                           (type x))
                      {:symbol x
                       :failure (str t)})))))

(defn- name-_
  "Return the name portion of a symbol, with dashes converted."
  [x]
  (try
    (str/replace (name x) "-" "_")
    (catch #?(:clj Throwable :cljs :default) t
      (throw (ex-info (str "expected symbol, found: "
                           (type x))
                      {:symbol x
                       :failure (str t)})))))

(defn format-entity
  "Given a simple SQL entity (a keyword or symbol -- or string),
  return the equivalent SQL fragment (as a string -- no parameters).

  Handles quoting, splitting at / or ., replacing - with _ etc."
  [e & [{:keys [aliased drop-ns]}]]
  (let [col-fn      (if (or *quoted* (string? e))
                      (if *quoted-snake* name-_ name)
                      name-_)
        col-e       (col-fn e)
        dialect-q   (:quote *dialect* identity)
        quote-fn    (cond (or *quoted* (string? e))
                          dialect-q
                          ;; #422: if default quoting and "unusual"
                          ;; characters in entity, then quote it:
                          (nil? *quoted*)
                          (fn opt-quote [part]
                            (if (re-find #"^[A-Za-z0-9_]+$" part)
                              part
                              (dialect-q part)))
                          :else
                          identity)
        parts       (if-let [n (when-not (or drop-ns (string? e))
                                 (namespace-_ e))]
                      [n col-e]
                      (if aliased
                        [col-e]
                        (str/split col-e #"\.")))
        entity      (str/join "." (map #(cond-> % (not= "*" %) (quote-fn)) parts))
        suspicious #";"]
    (when-not *allow-suspicious-entities*
      (when (re-find suspicious entity)
        (throw (ex-info (str "suspicious character found in entity: " entity)
                        {:disallowed suspicious}))))
    entity))

(comment
  (for [v [:foo-bar "foo-bar" ; symbol is the same as keyword
           :f-o.b-r :f-o/b-r]
        a [true false] d [true false] q [true false]]
    (binding [*dialect* (:mysql @dialects) *quoted* q]
      (if q
        [v a d (format-entity v {:aliased a :drop-ns d})
         (binding [*quoted-snake* true]
           (format-entity v {:aliased a :drop-ns d}))]
        [v a d (format-entity v {:aliased a :drop-ns d})])))
  )

(defn sql-kw
  "Given a keyword, return a SQL representation of it as a string.

  A keyword whose name begins with a single quote is left exactly as-is
  (with the `:` and `'` removed), otherwise a `:kebab-case` keyword
  becomes a `KEBAB CASE` (uppercase) string with hyphens replaced
  by spaces, e.g., `:insert-into` => `INSERT INTO`.

  Any namespace qualifier is ignored.

  Any ? is escaped to ??."
  [k]
  (let [n (str/replace (name k) "?" "??")]
    (if (= \' (first n))
      (let [ident   (subs n 1 (count n))
            ident-l (str/lower-case ident)]
        (binding [*quoted* (when-not (contains? #{"array"} ident-l) *quoted*)]
          (format-entity (keyword ident))))
      (-> n (dehyphen) (upper-case)))))

(defn- sym->kw
  "Given a symbol, produce a keyword, retaining the namespace
  qualifier, if any."
  [s]
  (if (symbol? s)
    (if-let [n (namespace s)]
      (keyword n (name s))
      (keyword (name s)))
    s))

(extend-protocol p/InlineValue
  nil
  (sqlize [_] "NULL")
  #?(:clj String :cljs string)
  (sqlize [x] (str \' (str/replace x "'" "''") \'))
  #?(:clj clojure.lang.Keyword :cljs Keyword)
  (sqlize [x] (sql-kw x))
  #?(:clj clojure.lang.Symbol :cljs Symbol)
  (sqlize [x] (sql-kw x))
  #?(:clj clojure.lang.IPersistentVector :cljs PersistentVector)
  (sqlize [x] (vector "[" (interpose ", " (map p/sqlize x)) "]"))
  #?@(:clj [java.util.UUID
            ;; issue 385: quoted UUIDs for PostgreSQL/ANSI
            (sqlize [x] (str \' x \'))])
  #?(:clj Object :cljs default)
  (sqlize [x] (str x)))

(defn- sqlize-value [x] (p/sqlize x))

(defn- param-value [k]
  (if (contains? *params* k)
    (get *params* k)
    (throw (ex-info (str "missing parameter value for " k)
                    {:params (keys *params*)}))))

(defn- ->param [k]
  (with-meta (constantly k)
    {::wrapper
     (fn [fk _] (param-value (fk)))}))

(def ^:private ^:dynamic *formatted-column* (atom false))

(defn- format-var [x & [opts]]
  ;; rather than name/namespace, we want to allow
  ;; for multiple / in the %fun.call case so that
  ;; qualified column names can be used:
  (let [c (cond-> (str x) (keyword? x) (subs 1))] 
    (cond (= \% (first c))
          (let [[f & args] (str/split (subs c 1) #"\.")
                quoted-args (map #(format-entity (keyword %) opts) args)]
            [(vector (upper-case (str/replace f "-" "_"))
                     "(" (interpose ", " quoted-args) ")")])
          (= \? (first c))
          (let [k (keyword (subs c 1))]
            (if *inline*
              [(sqlize-value (param-value k))]
              ["?" (->param k)]))
          (= \' (first c))
          (do
            (reset! *formatted-column* true)
            [(subs c 1)])
          :else
          [(format-entity x opts)])))

(defn- format-entity-alias [x]
  (cond (sequential? x)
        (let [s     (first x)
              pair? (< 1 (count x))]
          (when (map? s)
            (throw (ex-info "selectable cannot be statement!"
                            {:selectable s})))
          (let [[sql & params] (-format-expr s)]
            (into [(cond-> sql
                     pair?
                     (str (if (and (contains? *dialect* :as) (not (:as *dialect*))) " " " AS ")
                          (format-entity (second x) {:aliased true})))]
                  params)))

        :else
        [(format-entity x)]))

(comment
  (-format-expr :a)
  (-format-expr [:raw "My String"])
  (format-entity-alias [[:raw "My String"]])
  )

(declare format-selects-common)

(defn- format-selectable-dsl [x & [{:keys [as aliased] :as opts}]]
  (cond (map? x)
        (format-dsl x {:nested true})

        (sequential? x)
        (let [s     (first x)
              a     (second x)
              pair? (= 2 (count x))
              big?  (and (ident? s) (or (= "*" (name s)) (str/ends-with? (name s) ".*"))
                         (ident? a) (#{"except" "replace"} (name a)))
              more? (and (< 2 (count x)) (not big?))
              [sql & params] (if (map? s)
                               (format-dsl s {:nested true})
                               (-format-expr s))
              [sql' & params'] (when (or pair? big?)
                                 (cond (sequential? a)
                                       (let [[sqls params] (format-expr-list a {:aliased true})]
                                         (into [(interpose " " sqls)] params))
                                       big? ; BigQuery support #281
                                       (reduce (fn [[sql & params] [k arg]]
                                                 (let [[sql' params']
                                                       (cond (and (ident? k) (= "except" (name k)) arg)
                                                             (let [[sqls params]
                                                                   (format-expr-list arg {:aliased true})]
                                                               [(vector (sql-kw k) " (" (interpose ", " sqls) ")")
                                                                params])
                                                             (and (ident? k) (= "replace" (name k)) arg)
                                                             (let [[sql & params] (format-selects-common nil true arg)]
                                                               [(vector (sql-kw k) " (" sql ")")
                                                                params])
                                                             :else
                                                             (throw (ex-info "bigquery * only supports except and replace"
                                                                             {:clause k :arg arg})))]
                                                   (-> [(cond->> sql' sql (vector sql " "))]
                                                       (into params)
                                                       (into params'))))
                                               []
                                               (partition-all 2 (rest x)))
                                       :else
                                       (format-selectable-dsl a {:aliased true})))] 
          (-> [(cond pair?
                     (vector sql
                          (if as
                            (if (and (contains? *dialect* :as)
                                     (not (:as *dialect*)))
                              " "
                              " AS ")
                            " ") sql')
                     big?
                     (vector sql " " sql')
                     more?
                     (throw (ex-info "illegal syntax in select expression"
                                     {:symbol s :alias a :unexpected (nnext x)}))
                     :else
                     sql)]
              (into params)
              (into params')))

        (ident? x)
        (if aliased
          [(format-entity x opts)]
          (format-var x opts))

        (and aliased (string? x))
        [(format-entity x opts)]

        :else
        (-format-expr x)))

(defn- reduce-sql [xs]
  (reduce (fn [[sql params] [sql' & params']]
            [(conj sql sql') (if params' (into params params') params)])
          [[] []]
          xs))

;; primary clauses

(defn- format-on-set-op [k xs]
  (let [[sqls params] (reduce-sql (map #(format-dsl %) xs))]
    (into [(interpose (vector " " (sql-kw k) " ") sqls)] params)))

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
  (when-not (sequential? exprs)
    (throw (ex-info (str "format-expr-list expects a sequence of expressions, found: "
                         (type exprs))
                    {:exprs exprs})))
  (reduce-sql (map #(-format-expr % opts) exprs)))

(comment
  (format-expr-list :?tags)
  )

(defn- format-columns [k xs]
  (let [[sqls params] (format-expr-list xs {:drop-ns (= :columns k)})]
    (into [(vector "(" (interpose ", " sqls) ")")] params)))

(defn- format-selects-common [prefix as xs]
  (if (sequential? xs)
    (let [[sqls params] (reduce-sql (map #(format-selectable-dsl % {:as as}) xs))] 
      (when-not (= :none *checking*)
        (when (empty? xs)
          (throw (ex-info (str prefix " empty column list is illegal")
                          {:clause (into [prefix] xs)}))))
      (into [(vector (when prefix (vector prefix " ")) (interpose ", " sqls))] params))
    (let [[sql & params] (format-selectable-dsl xs {:as as})]
      (into [(vector (when prefix (vector prefix " ")) sql)] params))))

(defn- format-selects [k xs]
  (format-selects-common
   (sql-kw k)
   (#{:select :select-distinct :from :window :delete-from
      'select 'select-distinct 'from 'window 'delete-from}
    k)
   xs))

(defn- format-selects-on [_ xs]
  (let [[on & cols] xs
        [sql & params]
        (-format-expr (into [:distinct-on] on))
        [sql' & params']
        (format-selects-common
         (vector (sql-kw :select) " " sql)
         true
         cols)]
    (-> [sql'] (into params) (into params'))))

(defn- format-select-top [k xs]
  (let [[top & cols] xs
        [top & parts]
        (if (sequential? top)
          ;; could be an expression or a number followed by :percent :with-ties
          (let [top-q?    #(and (ident? %)
                                (#{:percent :with-ties} (sym->kw %)))
                r-top     (reverse top)
                top-quals (take-while top-q? r-top)
                top-list  (drop-while top-q? r-top)]
            (if (seq top-quals)
              (if (= 1 (count top-list))
                (into (vec top-list) (reverse top-quals))
                (throw (ex-info "unparseable TOP expression"
                                {:top top})))
              [top]))
          [top])
        [sql & params]
        (-format-expr top)
        [sql' & params']
        (format-selects-common
         (vector (sql-kw k) "(" sql ")"
              (when (seq parts) " ")
              (interpose " " (map sql-kw parts)))
         true
         cols)]
    (-> [sql'] (into params) (into params'))))

(defn- format-select-into [k xs]
  (let [[v e] (if (sequential? xs) xs [xs])
        [sql & params] (when e (-format-expr e))]
    (into [(vector (sql-kw k) " " (format-entity v)
                (when sql
                  (vector " "
                       (sql-kw (if (= :into k) :in :limit))
                       " "
                       sql)))]
          params)))

(defn- format-with-part [x]
  (if (sequential? x)
    (let [[sql & params] (format-dsl (second x))]
      (into [(vector (format-entity (first x)) " " sql)] params))
    [(format-entity x)]))

(defn- format-with [k xs as-fn]
  ;; TODO: a sequence of pairs -- X AS expr -- where X is either [entity expr]
  ;; or just entity, as far as I can tell...
  (let [[sqls params]
        (reduce-sql
         (map
          (fn [[x expr :as with]]
            (let [[sql & params] (format-with-part x)
                  non-query-expr? (or (ident? expr) (string? expr))
                  [sql' & params'] (if non-query-expr?
                                     (-format-expr expr)
                                     (format-dsl expr))]
              (if non-query-expr?
                (cond-> [(vector sql' " AS " sql)]
                        params' (into params')
                        params  (into params))
                ;; according to docs, CTE should _always_ be wrapped:
                (cond-> [(vector sql " " (as-fn with) " " (vector "(" sql' ")"))]
                        params  (into params)
                        params' (into params')))))
          xs))]
    (into [(vector (sql-kw k) " " (interpose ", " sqls))] params)))

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
                [sql & params] (format-dsl statement)
                [t-sql & t-params] (format-entity-alias table)
                [c-sqls c-params] (reduce-sql (map #'format-entity-alias cols))]
            (-> [(vector (sql-kw k) " " t-sql
                      " "
                      (when (seq cols)
                        (vector "("
                             (interpose ", " c-sqls)
                             ") "))
                      sql)]
                (into t-params)
                (into c-params)
                (into params)))
          (sequential? (second table))
          (let [[table cols] table
                [t-sql & t-params] (format-entity-alias table)
                [c-sqls c-params] (reduce-sql (map #'format-entity-alias cols))]
            (-> [(vector (sql-kw k) " " t-sql
                      " ("
                      (interpose ", " c-sqls)
                      ")")]
                (into t-params)
                (into c-params)))
          :else
          (let [[sql & params] (format-entity-alias table)]
            (into [(vector (sql-kw k) " " sql)] params)))
    (let [[sql & params] (format-entity-alias table)]
      (into [(vector (sql-kw k) " " sql)] params))))

(comment
  (format-insert :insert-into [[[:raw ":foo"]] {:select :bar}]))
  

(defn- format-join [k clauses]
  (let [[sqls params]
        (reduce (fn [[sqls params] [j e]]
                  (let [[sql-j & params-j]
                        (format-selects-common
                         (sql-kw (if (= :join k) :inner-join k))
                         true
                         [j])
                        sqls (conj sqls sql-j)]
                    (if (and (sequential? e) (= :using (first e)))
                      (let [[u-sqls u-params]
                            (reduce-sql (map #'format-entity-alias (rest e)))]
                        [(conj sqls
                               "USING"
                               (vector "("
                                    (interpose ", " u-sqls)
                                    ")"))
                         (-> params (into params-j) (into u-params))])
                      (let [[sql & params'] (when e (-format-expr e))]
                        [(cond-> sqls e (conj "ON" sql))
                         (-> params
                             (into params-j)
                             (into params'))]))))
                [[] []]
                (partition-all 2 clauses))]
    (into [(interpose " " sqls)] params)))

(def ^:private join-by-aliases
  "Map of shorthand to longhand join names."
  {:join  :inner-join
   :left  :left-join
   :right :right-join
   :inner :inner-join
   :outer :outer-join
   :full  :full-join
   :cross :cross-join})

(def ^:private valid-joins
  (set (vals join-by-aliases)))

(defn- format-join-by
  "Clauses should be a sequence of join types followed
  by their table and condition, or a sequence of join
  clauses, so that you can construct a series of joins
  in a specific order."
  [_ clauses]
  (let [joins (if (every? map? clauses)
                (into []
                      (comp (mapcat #(mapcat (juxt key val) %))
                            (map vector))
                      clauses)
                (partition-by ident? clauses))]
    (when-not (even? (count joins))
      (throw (ex-info ":join-by expects a sequence of join clauses"
                      {:clauses clauses})))
    (let [[sqls params]
          (reduce (fn [[sqls params] [[j] [clauses]]]
                    (let [j' (sym->kw j)
                          j' (sym->kw (join-by-aliases j' j'))]
                      (when-not (valid-joins j')
                        (throw (ex-info (str ":join-by found an invalid join type "
                                             j)
                                        {})))
                      (let [[sql' & params'] (format-dsl {j' clauses})]
                        [(conj sqls sql') (into params params')])))
                  [[] []]
                  (partition 2 joins))]
      (into [(interpose " " sqls)] params))))

(defn- format-on-expr [k e]
  (if (or (not (sequential? e)) (seq e))
    (let [[sql & params] (-format-expr e)]
      (into [(vector (sql-kw k) " " sql)] params))
    []))

(defn- format-group-by [k xs]
  (let [[sqls params] (format-expr-list xs)]
    (into [(vector (sql-kw k) " " (interpose ", " sqls))] params)))

(defn- format-order-by [k xs]
  (let [dirs (map #(when (sequential? %) (second %)) xs)
        [sqls params]
        (format-expr-list (map #(if (sequential? %) (first %) %) xs))]
    (into [(vector (sql-kw k) " "
                (interpose ", " (map (fn [sql dir]
                                      (vector sql " " (sql-kw (or dir :asc))))
                                    sqls
                                    dirs)))] params)))

(defn- format-lock-strength [k xs]
  (let [[strength tables nowait] (if (sequential? xs) xs [xs])]
    [(vector (sql-kw k) " " (sql-kw strength)
          (when tables
            (vector
              (cond (and (ident? tables)
                         (#{:nowait :skip-locked :wait} (sym->kw tables)))
                    (vector " " (sql-kw tables))
                    (sequential? tables)
                    (vector " OF "
                         (interpose ", " (map #'format-entity tables)))
                    :else
                    (vector " OF " (format-entity tables)))
              (when nowait
                (vector " " (sql-kw nowait))))))]))

(defn- format-values [k xs]
  (let [first-xs (when (sequential? xs) (first (drop-while ident? xs)))]
    (cond (contains? #{:default 'default} xs)
          [(vector (sql-kw xs) " " (sql-kw k))]
          (empty? xs)
          [(vector (sql-kw k) " ()")]
          (sequential? first-xs)
          ;; [[1 2 3] [4 5 6]]
          (let [n-1 (map count (filter sequential? xs))
                ;; issue #291: ensure all value sequences are the same length
                xs' (if (apply = n-1)
                      xs
                      (let [n-n (when (seq n-1) (apply max n-1))]
                        (map (fn [x]
                               (if (sequential? x)
                                 (take n-n (concat x (repeat nil)))
                                 x))
                             xs)))
                [sqls params]
                (reduce (fn [[sql params] [sqls' params']]
                          [(conj sql
                                 (if (sequential? sqls')
                                   (vector "(" (interpose ", " sqls') ")")
                                   sqls'))
                           (into params params')])
                        [[] []]
                        (map #(if (sequential? %)
                                (format-expr-list %)
                                [(sql-kw %)])
                             xs'))]
            (into [(vector (sql-kw k) " " (interpose ", " sqls))] params))

          (map? first-xs)
          ;; [{:a 1 :b 2 :c 3}]
          (let [cols-1 (keys (first xs))
                ;; issue #291: check for all keys in all maps but still
                ;; use the keys from the first map if they match so that
                ;; users can rely on the key ordering if they want to,
                ;; e.g., see test that uses array-map for the first row
                cols-n (into #{} (mapcat keys) (filter map? xs))
                cols   (if (= (set cols-1) cols-n) cols-1 cols-n)
                [sqls params]
                (reduce (fn [[sql params] [sqls' params']]
                          [(conj sql
                                 (if (sequential? sqls')
                                   (vector "(" (interpose ", " sqls') ")")
                                   sqls'))
                           (if params' (into params params') params')])
                        [[] []]
                        (map (fn [m]
                               (if (map? m)
                                 (format-expr-list
                                  (map #(get m
                                             %
                                             ;; issue #366: use NULL or DEFAULT
                                             ;; for missing column values:
                                             (if (contains? *values-default-columns* %)
                                               [:default]
                                               nil))
                                       cols))
                                 [(sql-kw m)]))
                             xs))]
            (into [(vector "("
                        (interpose ", "
                                  (map #(format-entity % {:drop-ns true}) cols))
                        ") "
                        (sql-kw k)
                        " "
                        (interpose ", " sqls))]
                  params))

          :else
          (throw (ex-info ":values expects a sequence of rows (maps) or column values (sequences)"
                          {:first (first xs)})))))

(comment
  (into #{} (mapcat keys) [{:a 1 :b 2} {:b 3 :c 4}])
  ,)

(defn- format-set-exprs [k xs]
  (let [[sqls params]
        (reduce-kv (fn [[sql params] v e]
                     (let [[sql' & params'] (-format-expr e)]
                       [(conj sql (vector (format-entity v {:drop-ns (not (mysql?))}) " = " sql'))
                        (if params' (into params params') params)]))
                   [[] []]
                   xs)]
    (into [(vector (sql-kw k) " " (interpose ", " sqls))] params)))

(defn- format-on-conflict [k x]
  (if (sequential? x)
    (let [entities (take-while ident? x)
          n (count entities)
          [clause & more] (drop n x)
          _ (when (or (seq more)
                      (and clause (not (map? clause))))
              (throw (ex-info "unsupported :on-conflict format"
                              {:clause x})))
          [sql & params] (when clause
                           (format-dsl clause))]
      (into [(vector (sql-kw k)
                  (when (pos? n)
                    (vector " ("
                         (interpose ", " (map #'format-entity entities))
                         ")"))
                  (when sql
                    (vector " " sql)))]
            params))
    (format-on-conflict k [x])))

(defn- format-do-update-set [k x]
  (cond (map? x)
        (if (or (contains? x :fields) (contains? x 'fields))
          (let [sets (interpose ", "
                               (map (fn [e]
                                      (let [e (format-entity e {:drop-ns true})]
                                        (vector e " = EXCLUDED." e)))
                                    (or (:fields x)
                                        ('fields x))))
                where (or (:where x) ('where x))
                [sql & params] (when where (format-dsl {:where where}))]
            (into [(vector (sql-kw k) " " sets
                        (when sql (vector " " sql)))] params))
          (format-set-exprs k x))
        (sequential? x)
        (let [[cols clauses] (split-with (complement map?) x)]
          (if (seq cols)
            (recur k {:fields cols :where (:where (first clauses))})
            (recur k (first clauses))))
        :else
        (let [e (format-entity x {:drop-ns true})]
          [(vector (sql-kw k) " " e " = EXCLUDED." e)])))

(defn- format-simple-clause [c context]
  (binding [*inline* true]
    (let [[sql & params] (format-dsl c)]
      (when (seq params)
        (throw (ex-info (str "parameters are not accepted in " context)
                        {:clause c :params params})))
      sql)))

(defn- format-simple-expr [e context]
  (binding [*inline* true]
    (let [[sql & params] (-format-expr e)]
      (when (seq params)
        (throw (ex-info (str "parameters are not accepted in " context)
                        {:expr e :params params})))
      sql)))

(defn- format-alter-table [k x]
  (if (sequential? x)
    [(vector (sql-kw k) " " (format-entity (first x))
          (when-let [clauses (next x)] 
            (vector " " (interpose ", " (map #(format-simple-clause % "column/index operations") clauses)))))]
    [(vector (sql-kw k) " " (format-entity x))]))

(def ^:private special-ddl-keywords
  "If these are found in DDL, they should map to the given
  SQL string instead of what sql-kw would do."
  {:auto-increment "AUTO_INCREMENT"})

(defn- sql-kw-ddl
  "Handle SQL keywords in DDL (allowing for special/exceptions)."
  [id]
  (or (get special-ddl-keywords (sym->kw id))
      (sql-kw id)))

(defn- format-ddl-options
  "Given a sequence of options for a DDL statement (the part that
  comes between the entity name being created/dropped and the
  remaining part of the statement), render clauses and sequences
  of keywords and entity names. Returns a sequence of SQL strings."
  [opts context]
  (for [opt opts]
    (cond (map? opt)
          (format-simple-clause opt context)
          (sequential? opt)
          (interpose " "
                    (map (fn [e]
                           (if (ident? e)
                             (sql-kw-ddl e)
                             (format-simple-expr e context)))
                         opt))
          (ident? opt)
          (sql-kw-ddl opt)
          :else
          (throw (ex-info "expected symbol or keyword"
                          {:unexpected opt})))))

(defn- destructure-ddl-item [table context]
  (let [params
        (if (sequential? table)
          table
          [table])
        tab? #(or (ident? %) (string? %))
        coll (take-while tab? params)
        opts (filter some? (drop-while tab? params))
        ine  (last coll)
        [prequel table ine]
        (if (= :if-not-exists (sym->kw ine))
          [(butlast (butlast coll)) (last (butlast coll)) ine]
          [(butlast coll) (last coll) nil])]
    (into [(interpose " " (map sql-kw prequel))
           (when table (format-entity table))
           (when ine (sql-kw ine))]
          (when opts
            (format-ddl-options opts context)))))

(defn- format-truncate [k xs]
  (let [[table & options] (if (sequential? xs) xs [xs])
        [pre table ine options] (destructure-ddl-item [table options] "truncate")]
    (when (seq pre) (throw (ex-info "TRUNCATE syntax error" {:unexpected pre})))
    (when (seq ine) (throw (ex-info "TRUNCATE syntax error" {:unexpected ine})))
    [(interpose " " (cond-> [(sql-kw k) table]
                     (seq options)
                     (conj options)))]))

(comment
  (destructure-ddl-item [:foo [:abc [:continue :wibble] :identity]] "test")
  (destructure-ddl-item [:foo] "test")
  (destructure-ddl-item [:id [:int :unsigned :auto-increment]] "test")
  (destructure-ddl-item [[[:foreign-key :bar]] :quux [[:wibble :wobble]]] "test")
  (format-truncate :truncate [:foo]))
  

(defn- format-create [q k item as]
  (let [[pre entity ine & more]
        (destructure-ddl-item item (vector (sql-kw q) " options"))]
    [(interpose " " (remove nil?
                           (-> [(sql-kw q)
                                (when (and (= :create q) (seq pre)) pre)
                                (sql-kw k)
                                ine
                                (when (and (= :refresh q) (seq pre)) pre)
                                entity]
                               (into more)
                               (conj (when as (sql-kw as))))))]))

(defn- format-with-data [_ data]
  (let [data (if (sequential? data) (first data) data)]
    [(interpose " " (remove nil?
                           [(sql-kw :with)
                            (when-not data (sql-kw :no))
                            (sql-kw :data)]))]))

(defn- destructure-drop-items [tables context]
  (let [params
        (if (sequential? tables)
          tables
          [tables])
        coll (take-while ident? params)
        opts (drop-while ident? params)
        [if-exists & tables]
        (if (#{:if-exists 'if-exists} (first coll))
          coll
          (cons nil coll))]
    (into [(when if-exists (sql-kw :if-exists))
           (interpose ", " (map #'format-entity tables))]
          (format-ddl-options opts context))))

(defn- format-drop-items
  [k params]
  (let [[if-exists tables & more] (destructure-drop-items params "DROP options")]
    [(interpose " " (remove nil? (into [(sql-kw k) if-exists tables] more)))]))

(defn- format-single-column [xs]
  (let [[col & options] (if (ident? (first xs)) xs (cons nil xs))
        [pre col ine & options]
        (destructure-ddl-item [col options] "column operation")]
    (when (seq pre) (throw (ex-info "column syntax error" {:unexpected pre})))
    (when (seq ine) (throw (ex-info "column syntax error" {:unexpected ine})))
    (interpose " " (filter seq (cons col options)))))

(comment
  (destructure-ddl-item [:foo [:abc [:continue :wibble] :identity]] "test")
  (destructure-ddl-item [:foo] "test")
  (destructure-ddl-item [:id [:int :unsigned :auto-increment]] "test")
  (format-single-column [:id :int :unsigned :auto-increment])
  (format-single-column [[:constraint :code_title] [:primary-key :code :title]])
  (destructure-ddl-item [[[:foreign-key :bar]] :quux [[:wibble :wobble]]] "test")

  (format-truncate :truncate [:foo])

  (destructure-ddl-item [:address [:text]] "test")
  (format-single-column [:address :text])
  (format-single-column [:did :uuid [:default [:gen_random_uuid]]])
  )

(defn- format-table-columns [_ xs]
  [(vector "("
        (interpose ", " (map #'format-single-column xs))
        ")")])

(defn- format-add-single-item [k spec]
  (if (contains? #{:if-not-exists 'if-not-exists} (last spec))
    (vector (sql-kw k) " " (sql-kw :if-not-exists) " " (format-single-column (butlast spec)))
    (vector (sql-kw k) " " (format-single-column spec))))

(defn- format-add-item [k spec]
  (let [items (if (and (sequential? spec) (sequential? (first spec))) spec [spec])]
    [(interpose ", " (for [item items] (format-add-single-item k item)))]))

(comment
  (format-add-item :add-column [:address :text])
  (format-add-single-item :add-column [:address :text])
  (format-single-column [:address :text]))
  

(defn- format-rename-item [k [x y]]
  [(vector (sql-kw k) " " (format-entity x) " TO " (format-entity y))])

(defn- raw-render [s]
  (if (sequential? s)
    (let [[sqls params]
          (reduce (fn [[sqls params] s]
                    (if (sequential? s)
                      (let [[sql & params'] (-format-expr s)]
                        [(conj sqls sql)
                         (into params params')])
                      [(conj sqls s) params]))
                  [[] []]
                  s)]
      (into [sqls] params))
    [s]))

(defn- destructure-drop-columns [tables]
  (let [params
        (if (sequential? tables)
          tables
          [tables])
        _    (when-not (every? ident? params)
               (throw (ex-info "DROP COLUMNS expects just column names"
                               {:tables tables})))]
    (loop [if-exists false coll params sqls []]
      (if (seq coll)
        (if (#{:if-exists 'if-exists} (first coll))
          (recur true (rest coll) sqls)
          (recur false (rest coll)
                 (conj sqls (cond->> (format-entity (first coll))
                              if-exists
                              (vector (sql-kw :if-exists) " ")))))
        (if if-exists
          (throw (ex-info (str "DROP COLUMNS: missing column name after IF EXISTS")
                          {:tables tables}))
          sqls)))))

(defn- format-drop-columns
  [k params]
  (let [tables (destructure-drop-columns params)]
    [(interpose ", " (mapv #(vector (sql-kw k) " " %) tables))]))

(defn- check-where
  "Given a formatter function, performs a pre-flight check that there is
  a non-empty where clause if at least basic checking is enabled."
  [formatter]
  (fn [k xs]
    (when-not (= :none *checking*)
      (when-not (seq (:where *dsl*))
        (throw (ex-info (str (sql-kw k) " without a non-empty WHERE clause is dangerous")
                        {:clause k :where (:where *dsl*)}))))
    (formatter k xs)))

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
         :drop-column     #'format-drop-columns
         :alter-column    (fn [k spec]
                            (format-add-item
                             (if (mysql?) :modify-column k)
                             spec))
         :modify-column   #'format-add-item
         :rename-column   #'format-rename-item
         ;; so :add-index works with both [:index] and [:unique]
         :add-index       (fn [_ x] (format-on-expr :add x))
         :drop-index      #'format-selector
         :rename-table    (fn [_ x] (format-selector :rename-to x))
         :create-table    (fn [_ x] (format-create :create :table x nil))
         :create-table-as (fn [_ x] (format-create :create :table x :as))
         :create-extension (fn [_ x] (format-create :create :extension x nil))
         :with-columns    #'format-table-columns
         :create-view     (fn [_ x] (format-create :create :view x :as))
         :create-materialized-view (fn [_ x] (format-create :create :materialized-view x :as))
         :drop-table      #'format-drop-items
         :drop-extension  #'format-drop-items
         :drop-view       #'format-drop-items
         :drop-materialized-view #'format-drop-items
         :refresh-materialized-view (fn [_ x] (format-create :refresh :materialized-view x nil))
         :raw             (fn [_ x] (raw-render x))
         :nest            (fn [_ x]
                            (let [[sql & params] (format-dsl x {:nested true})]
                              (into [sql] params)))
         :with            (let [as-fn
                                (fn [[_ _ materialization]]
                                  (condp = materialization
                                    :materialized "AS MATERIALIZED"
                                    :not-materialized "AS NOT MATERIALIZED"
                                    "AS"))]
                            (fn [k xs] (format-with k xs as-fn)))
         :with-recursive  (let [as-fn (constantly "AS")]
                            (fn [k xs] (format-with k xs as-fn)))
         :intersect       #'format-on-set-op
         :union           #'format-on-set-op
         :union-all       #'format-on-set-op
         :except          #'format-on-set-op
         :except-all      #'format-on-set-op
         :table           #'format-selector
         :select          #'format-selects
         :select-distinct #'format-selects
         :select-distinct-on #'format-selects-on
         :select-top      #'format-select-top
         :select-distinct-top #'format-select-top
         :into            #'format-select-into
         :bulk-collect-into #'format-select-into
         :insert-into     #'format-insert
         :update          (check-where #'format-selector)
         :delete          (check-where #'format-selects)
         :delete-from     (check-where #'format-selector)
         :truncate        #'format-truncate
         :columns         #'format-columns
         :set             #'format-set-exprs
         :from            #'format-selects
         :using           #'format-selects
         :join-by         #'format-join-by
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
         :offset          (fn [_ x]
                            (if (or (contains-clause? :fetch) (sql-server?))
                              (let [[sql & params] (format-on-expr :offset x)
                                    rows (if (and (number? x) (== 1 x)) :row :rows)]
                                (into [(vector sql " " (sql-kw rows))] params))
                              ;; format in the old style:
                              (format-on-expr :offset x)))
         :fetch           (fn [_ x]
                            (let [which (if (contains-clause? :offset) :fetch-next :fetch-first)
                                  rows  (if (and (number? x) (== 1 x)) :row-only :rows-only)
                                  [sql & params] (format-on-expr which x)]
                              (into [(vector sql " " (sql-kw rows))] params)))
         :for             #'format-lock-strength
         :lock            #'format-lock-strength
         :values          #'format-values
         :on-conflict     #'format-on-conflict
         :on-constraint   #'format-selector
         :do-nothing      (fn [k _] (vector (sql-kw k)))
         :do-update-set   #'format-do-update-set
         ;; MySQL-specific but might as well be always enabled:
         :on-duplicate-key-update #'format-do-update-set
         :returning       #'format-selects
         :with-data       #'format-with-data}))

(assert (= (set @base-clause-order)
           (set @current-clause-order)
           (set (keys @clause-format))))

(defn- kw->sym
  "Given a keyword, produce a symbol, retaining the namespace
  qualifier, if any."
  [k]
  (if (keyword? k)
    (if-let [n (namespace k)]
      (symbol n (name k))
      (symbol (name k)))
    k))

(defn format-dsl
  "Given a hash map representing a SQL statement and a hash map
  of options, return a vector containing a string -- the formatted
  SQL statement -- followed by any parameter values that SQL needs.

  This is intended to be used when writing your own formatters to
  extend the DSL supported by HoneySQL."
  [statement-map & [{:keys [aliased nested pretty]}]]
  (binding [*dsl* statement-map]
    (let [[sqls params leftover]
          (reduce (fn [[sql params leftover] k]
                    (if-some [xs (if-some [xs (k leftover)]
                                   xs
                                   (let [s (kw->sym k)]
                                     (get leftover s)))]
                      (let [formatter (k @clause-format)
                            [sql' & params'] (formatter k xs)]
                        [(conj sql sql')
                         (if params' (into params params') params)
                         (dissoc leftover k (kw->sym k))])
                      [sql params leftover]))
                  [[] [] statement-map]
                  *clause-order*)]
      (if (seq leftover)
        (throw (ex-info (str "These SQL clauses are unknown or have nil values: "
                             (str/join ", " (keys leftover)))
                        leftover))
        (into [(cond-> (interpose (if pretty "\n" " ") (filter seq sqls))
                 pretty
                 (as-> s (vector "\n" s "\n"))
                 (and nested (not aliased))
                 (as-> s (vector "(" s ")")))] params)))))

(def ^:private infix-aliases
  "Provided for backward compatibility with earlier HoneySQL versions."
  {:not= :<>
   :!= :<>
   :regex :regexp})

(def ^:private infix-ops
  (-> #{"mod" "and" "or" "xor" "<>" "<=" ">=" "||" "<->"
        "like" "not-like" "regexp" "&&"
        "ilike" "not-ilike" "similar-to" "not-similar-to"
        "is" "is-not" "not=" "!=" "regex"}
      (into (map str "+-*%|&^=<>"))
      (into (keys infix-aliases))
      (into (vals infix-aliases))
      (->> (into #{} (map keyword)))
      (conj :/) ; because (keyword "/") does not work in cljs
      (atom)))

(def ^:private op-ignore-nil (atom #{:and :or}))
(def ^:private op-variadic   (atom #{:and :or :+ :* :|| :&&}))

(defn- unwrap [x opts]
  (if-let [m (meta x)]
    (if-let [f (::wrapper m)]
      (f x opts)
      x)
    x))

(defn- format-in [in [x y]]
  (let [[sql-x & params-x] (-format-expr x {:nested true})
        [sql-y & params-y] (-format-expr y {:nested true})
        values             (unwrap (first params-y) {})]
    ;; #396: prevent caching IN () when named parameter is used:
    (when (and (meta (first params-y))
               (::wrapper (meta (first params-y)))
               *caching*)
      (throw (ex-info "SQL that includes IN () expressions cannot be cached" {})))
    (when-not (= :none *checking*)
      (when (or (and (sequential? y)      (empty? y))
                (and (sequential? values) (empty? values)))
        (throw (ex-info "IN () empty collection is illegal"
                        {:clause [in x y]})))
      (when (and (= :strict *checking*)
                 (or (and (sequential? y)      (some nil? y))
                     (and (sequential? values) (some nil? values))))
        (throw (ex-info "IN (NULL) does not match"
                        {:clause [in x y]}))))
    (if (and (= :parameter-marker sql-y) (= 1 (count params-y)) (coll? values))
      (let [sql (vector "(" (interpose ", " (repeat (count values) :parameter-marker)) ")")]
        (-> [(vector sql-x " " (sql-kw in) " " sql)]
            (into params-x)
            (into values)))
      (-> [(vector sql-x " " (sql-kw in) " " sql-y)]
          (into params-x)
          (into params-y)))))

(defn- function-0 [k xs]
  [(vector (sql-kw k)
        (when (seq xs)
          (vector "("
               (interpose ", "
                         (map #(format-simple-expr % "column/index operation")
                              xs))
               ")")))])

(defn- function-1 [k xs]
  [(vector (sql-kw k)
        (when (seq xs)
          (vector " " (format-simple-expr (first xs)
                                       "column/index operation")
               (when-let [args (next xs)]
                 (vector "("
                      (interpose ", "
                                 (map #(format-simple-expr % "column/index operation")
                                      args))
                      ")")))))])

(defn- function-1-opt [k xs]
  [(vector (sql-kw k)
        (when (seq xs)
          (vector (when-let [e (first xs)]
                   (vector " " (format-simple-expr e "column/index operation")))
               (when-let [args (next xs)]
                 (vector "("
                      (interpose ", "
                                (map #(format-simple-expr % "column/index operation")
                                     args))
                      ")")))))])

(defn- expr-clause-pairs
  "For FILTER and WITHIN GROUP that have an expression
  followed by a SQL clause."
  [k pairs]
  (let [[sqls params]
        (reduce (fn [[sqls params] [e c]]
                  (let [[sql-e & params-e] (-format-expr e)
                        [sql-c & params-c] (format-dsl c {:nested true})]
                    [(conj sqls (vector sql-e " " (sql-kw k) " " sql-c))
                     (-> params (into params-e) (into params-c))]))
                [[] []]
                (partition 2 pairs))]
    (into [(interpose ", " sqls)] params)))

(defn- case-clauses
  "For both :case and :case-expr."
  [k clauses]
  (let [case-expr? (= :case-expr k)
        [sqlx & paramsx] (when case-expr? (-format-expr (first clauses)))
        [sqls params]
        (reduce (fn [[sqls params] [condition value]]
                  (let [[sqlc & paramsc] (when-not (= :else condition)
                                           (-format-expr condition))
                        [sqlv & paramsv] (-format-expr value)]
                    [(if (or (= :else condition)
                             (= 'else condition))
                       (conj sqls (sql-kw :else) sqlv)
                       (conj sqls (sql-kw :when) sqlc (sql-kw :then) sqlv))
                     (-> params (into paramsc) (into paramsv))]))
                [[] []]
                (partition 2 (if case-expr? (rest clauses) clauses)))]
    (-> [(vector (sql-kw :case) " "
              (when case-expr?
                (vector sqlx " "))
              (interpose " " sqls)
              " " (sql-kw :end))]
        (into paramsx)
        (into params))))

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
    ;; used in DDL to force rendering as a SQL entity instead
    ;; of a SQL keyword:
    :entity      (fn [_ [e]] [(format-entity e)])
    ;; bigquery column types:
    :bigquery/array (fn [_ spec]
                      [(vector "ARRAY<"
                            (interpose " " (map #(sql-kw %) spec))
                            ">")])
    :bigquery/struct (fn [_ spec]
                       [(vector "STRUCT<"
                             (interpose ", " (map format-single-column spec))
                             ">")])
    :array
    (fn [_ [arr]]
      ;; allow for (unwrap arr) here?
      (let [[sqls params] (format-expr-list arr)]
        (into [(vector "ARRAY[" (interpose ", " sqls) "]")] params)))
    :between
    (fn [_ [x a b]]
      (let [[sql-x & params-x] (-format-expr x {:nested true})
            [sql-a & params-a] (-format-expr a {:nested true})
            [sql-b & params-b] (-format-expr b {:nested true})]
        (-> [(vector sql-x " BETWEEN " sql-a " AND " sql-b)]
            (into params-x)
            (into params-a)
            (into params-b))))
    :case      #'case-clauses
    :case-expr #'case-clauses
    :cast
    (fn [_ [x type]]
      (let [[sql & params]   (-format-expr x)
            [sql' & params'] (if (ident? type)
                               [(sql-kw type)]
                               (-format-expr type))]
        (-> [(vector "CAST(" sql " AS " sql' ")")]
            (into params)
            (into params'))))
    :composite
    (fn [_ [& args]]
      (let [[sqls params] (format-expr-list args)]
        (into [(vector "(" (interpose ", " sqls) ")")] params)))
    :distinct
    (fn [_ [x]]
      (let [[sql & params] (-format-expr x {:nested true})]
        (into [(vector "DISTINCT " sql)] params)))
    :escape
    (fn [_ [pattern escape-chars]]
      (let [[sql-p & params-p] (-format-expr pattern)
            [sql-e & params-e] (-format-expr escape-chars)]
        (-> [(vector sql-p " " (sql-kw :escape) " " sql-e)]
            (into params-p)
            (into params-e))))
    :filter expr-clause-pairs
    :inline
    (fn [_ [x]]
      (binding [*inline* true]
        (-format-expr x)))
    :interval
    (fn [_ [n units]]
      (let [[sql & params] (-format-expr n)]
        (into [(vector "INTERVAL " sql " " (sql-kw units))] params)))
    :lateral
    (fn [_ [clause-or-expr]]
      (if (map? clause-or-expr)
        (let [[sql & params] (format-dsl clause-or-expr)]
          (into [(vector "LATERAL (" sql ")")] params))
        (let [[sql & params] (-format-expr clause-or-expr)]
          (into [(vector "LATERAL " sql)] params))))
    :lift
    (fn [_ [x]]
      (if *inline*
        ;; this is pretty much always going to be wrong,
        ;; but it could produce a valid result so we just
        ;; assume that the user knows what they are doing:
        [(sqlize-value x)]
        [:parameter-marker (with-meta (constantly x)
                                   {::wrapper (fn [fx _] (fx))})]))
    :nest
    (fn [_ [x]]
      (let [[sql & params] (-format-expr x)]
        (into [(vector "(" sql ")")] params)))
    :not
    (fn [_ [x]]
      (let [[sql & params] (-format-expr x {:nested true})]
        (into [(vector "NOT " sql)] params)))
    :order-by
    (fn [k [e q]]
      (let [[sql-e & params-e] (-format-expr e)
            [sql-q & params-q] (format-dsl {k [q]})]
        (-> [(vector sql-e " " sql-q)]
            (into params-e)
            (into params-q))))
    :over
    (fn [_ [& args]]
      (let [[sqls params]
            (reduce (fn [[sqls params] [e p a]]
                      (let [[sql-e & params-e] (-format-expr e)
                            [sql-p & params-p] (if (or (nil? p) (map? p))
                                                 (format-dsl p {:nested true})
                                                 [(format-entity p)])]
                        [(conj sqls (vector sql-e " OVER " sql-p
                                         (when a (vector " AS " (format-entity a)))))
                         (-> params (into params-e) (into params-p))]))
                    [[] []]
                    args)]
        (into [(interpose ", " sqls)] params)))
    :param
    (fn [_ [k]]
      (if *inline*
        [(sqlize-value (param-value k))]
        [:parameter-marker (->param k)]))
    :raw
    (fn [_ [xs]]
      (raw-render xs))
    :within-group expr-clause-pairs}))

(defn- format-sql-fragments
  "Reduce nested SQL fragments into a SQL string. parameter-marker will be replaced with
   proper marker depending on *marker-formatter*."
  [sqls]
  (let [sb (StringBuffer.)
        idx (atom *marker-init-index*)
        sqls' (->> (if (sequential? sqls)
                     sqls (vector sqls))
                   (flatten))
        marker-formatter (case *marker-formatter*
                           "?" (constantly "?")
                           "$" (fn [idx _]
                                 (str "$" idx))
                           (if (fn? *marker-formatter*)
                             *marker-formatter*
                             (throw (ex-info "*marker-formatter* should either '?', '$' or custom fn" 
                                             {:marker-formatter *marker-formatter*}))))]
    (run!
     (fn [sql]
       (cond
         (= sql :parameter-marker)
         (do (.append sb (marker-formatter @idx sql))
             (swap! idx inc))
         (not (nil? sql))
         (.append sb sql)))
     sqls')
    (.toString sb)))

(defn- internal->sql
  [[sqls & params]]
  (into [(format-sql-fragments sqls)] params))

(defn -format-expr
  "Given a data structure that represents a SQL expression and a hash
  map of options, return a pair where the first element is a sequence of SQL fragments and the second
  element is a sequence of parameters.

  This is intended to be used when writing your own formatters to
  extend the DSL supported by HoneySQL."
  [expr & [{:keys [nested] :as opts}]]
  (cond (ident? expr)
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
                          (reduce-sql (map #(-format-expr % {:nested true})
                                           (rest x)))]
                      (into [(cond-> (interpose (vector " " (sql-kw op) " ") sqls)
                               nested
                               (as-> s (vector "(" s ")")))]
                            params))
                    (let [[_ a b & y] expr
                          _           (when (seq y)
                                        (throw (ex-info (str "only binary "
                                                             op
                                                             " is supported")
                                                        {:expr expr})))
                          [s1 & p1]   (-format-expr a {:nested true})
                          [s2 & p2]   (-format-expr b {:nested true})
                          op          (get infix-aliases op op)]
                      (-> (if (and (#{:= :<>} op) (or (nil? a) (nil? b)))
                            (vector (if (nil? a)
                                     (if (nil? b) "NULL" s2)
                                     s1)
                                 (if (= := op) " IS NULL" " IS NOT NULL"))
                            (vector s1 " " (sql-kw op) " " s2))
                          (cond-> nested
                            (as-> s (vector "(" s ")")))
                          (vector)
                          (into p1)
                          (into p2))))
                  (contains? #{:in :not-in} op)
                  (let [[sql & params] (format-in op (rest expr))]
                    (into [(if nested (vector "(" sql ")") sql)] params))
                  (contains? @special-syntax op)
                  (let [formatter (get @special-syntax op)]
                    (formatter op (rest expr)))
                  :else
                  (let [args          (rest expr)
                        [sqls params] (format-expr-list args)]
                    (into [(vector (sql-kw op)
                                (if (and (= 1 (count args))
                                         (map? (first args))
                                         (= 1 (count sqls)))
                                  (vector " " (first sqls))
                                  (vector "(" (interpose ", " sqls) ")")))]
                          params)))
            (let [[sqls params] (format-expr-list expr)]
              (into [(vector "(" (interpose ", " sqls) ")")] params))))

        (boolean? expr)
        [(upper-case (str expr))]

        (nil? expr)
        ["NULL"]

        :else
        (if *inline*
          [(sqlize-value expr)]
          [:parameter-marker expr])))

(defn format-expr
  "Given a data structure that represents a SQL expression and a hash
  map of options, return a vector containing a string -- the formatted
  SQL statement -- followed by any parameter values that SQL needs.

  This is intended to be used when debugging your own formatters to
  extend the DSL supported by HoneySQL."
  [expr & opts]
  (-> (apply -format-expr expr opts) (internal->sql)))

(defn- check-dialect [dialect]
  (when-not (contains? @dialects dialect)
    (throw (ex-info (str "Invalid dialect: " dialect)
                    {:valid-dialects (vec (sort (keys @dialects)))})))
  dialect)

(def through-opts
  "If org.clojure/core.cache is available, resolves to a function that
  calls core.cache.wrapped/lookup-or-miss, otherwise to a function that
  throws an exception.

  In ClojureScript, a resolves to a function that throws an exception
  because core.cache relies on JVM machinery and is Clojure-only."
  #?(:clj (try (require 'clojure.core.cache.wrapped)
               (let [lookup-or-miss (deref (resolve 'clojure.core.cache.wrapped/lookup-or-miss))]
                 (fn [_opts cache data f]
                   (lookup-or-miss cache data f)))
               (catch Throwable _
                 (fn [opts _cache _data _f]
                   (throw (ex-info "include core.cached on the classpath to use the :cache option" opts)))))
     :cljs (fn [opts _cache _data _f]
             (throw (ex-info "cached queries are not supported in ClojureScript" opts)))))

(defn format
  "Turn the data DSL into a vector containing a SQL string followed by
  any parameter values that were encountered in the DSL structure.

  This is the primary API for HoneySQL and handles dialects, quoting,
  and named parameters.

  `format` accepts options as either a single hash map argument or
  as named arguments (alternating keys and values). If you are using
  Clojure 1.11 (or later) you can mix'n'match, providing some options
  as named arguments followed by other options in a hash map."
  ([data] (format data {}))
  ([data opts]
   (let [cache    (:cache opts)
         dialect? (contains? opts :dialect)
         dialect  (when dialect? (get @dialects (check-dialect (:dialect opts))))]
     (binding [*dialect* (if dialect? dialect @default-dialect)
               *caching* cache
               *marker-formatter* (if (contains? opts :marker-formatter)
                                    (:marker-formatter opts)
                                    @default-marker-formatter)
               *marker-init-index* (if (contains? opts :marker-init-index)
                                     (:marker-init-index opts)
                                     @default-marker-init-index)
               *checking* (if (contains? opts :checking)
                            (:checking opts)
                            @default-checking)
               *clause-order* (if dialect?
                                (if-let [f (:clause-order-fn dialect)]
                                  (f @base-clause-order)
                                  @current-clause-order)
                                @current-clause-order)
               *inline*  (if (contains? opts :inline)
                           (:inline opts)
                           @default-inline)
               *quoted*  (cond (contains? opts :quoted)
                               (:quoted opts)
                               dialect?
                               true
                               :else
                               @default-quoted)
               *quoted-snake* (if (contains? opts :quoted-snake)
                                (:quoted-snake opts)
                                @default-quoted-snake)
               *params* (:params opts)
               *values-default-columns* (:values-default-columns opts)]
       (if cache
         (->> (through-opts opts cache data (fn [_] (-> (format-dsl data (dissoc opts :cache))
                                                        (internal->sql))))
              (mapv #(unwrap % opts)))
         (mapv #(unwrap % opts) (-> (format-dsl data opts)
                                    (internal->sql)))))))
  ([data k v & {:as opts}] (format data (assoc opts k v))))

(defn set-dialect!
  "Set the default dialect for formatting.

  Can be: `:ansi` (the default), `:mysql`, `:oracle`, or `:sqlserver`.

  Can optionally accept `:quoted true` (or `:quoted false`) to set the
  default global quoting strategy. Without `:quoted`, the default global
  quoting strategy will be reset (only quoting unusual entity names).

  Note that calling `set-options!` can override this default.

  Dialects are always applied to the base order to create the current order."
  [dialect & {:keys [quoted]}]
  (reset! default-dialect (get @dialects (check-dialect dialect)))
  (when-let [f (:clause-order-fn @default-dialect)]
    (reset! current-clause-order (f @base-clause-order)))
  (reset! default-quoted quoted))

(defn set-options!
  "Set default values for any or all of the following options:
  * :checking
  * :inline
  * :quoted
  * :quoted-snake
  Note that calling `set-dialect!` can override the default for `:quoted`."
  [opts]
  (let [unknowns (dissoc opts :checking :inline :quoted :quoted-snake)]
    (when (seq unknowns)
      (throw (ex-info (str (str/join ", " (keys unknowns))
                           " are not options that can be set globally.")
                      unknowns)))
    (when (contains? opts :checking)
      (reset! default-checking (:checking opts)))
    (when (contains? opts :inline)
      (reset! default-checking (:inline opts)))
    (when (contains? opts :quoted)
      (reset! default-checking (:quoted opts)))
    (when (contains? opts :quoted-snake)
      (reset! default-checking (:quoted-snake opts)))))

(defn clause-order
  "Return the current order that known clauses will be applied when
  formatting a data structure into SQL. This may be useful when you are
  figuring out the `before` argument of `register-clause!` as well as
  for debugging new clauses you have registered."
  []
  @current-clause-order)

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
  MySQL.

  Use `clause-order` to see the full ordering of existing clauses."
  [clause formatter before]
  (let [clause (sym->kw clause)
        before (sym->kw before)]
    (assert (keyword? clause))
    (let [k (sym->kw formatter)
          f (if (keyword? k)
              (get @clause-format k)
              formatter)]
      (when-not (and f (or (fn? f) (and (var? f) (fn? (deref f)))) )
        (throw (ex-info "The formatter must be a function or existing clause"
                        {:type (type formatter)})))
      (swap! base-clause-order add-clause-before clause before)
      (swap! current-clause-order add-clause-before clause before)
      (swap! clause-format assoc clause f))))

(defn register-dialect!
  "Register a new dialect. Accepts a dialect name (keyword) and a hash
  map that must contain at least a `:quoted` key whose value is a unary
  function that accepts a string and returns it quoted per the dialect.

  It may also contain a `:clause-order-fn` key whose value is a unary
  function that accepts a list of SQL clauses (keywords) in order of
  precedence and returns an updated list of SQL clauses in order. It
  may use `add-clause-before` to achieve this. Currently, the only
  dialect that does this is MySQL, whose `SET` clause (`:set`) has a
  non-standard precedence, compared to other SQL dialects."
  [dialect dialect-spec]
  (when-not (keyword? dialect)
    (throw (ex-info "Dialect must be a keyword" {:dialect dialect})))
  (when-not (map? dialect-spec)
    (throw (ex-info "Dialect spec must be a hash map containing at least a :quote function"
                    {:dialect-spec dialect-spec})))
  (when-not (fn? (:quote dialect-spec))
    (throw (ex-info "Dialect spec is missing a :quote function"
                    {:dialect-spec dialect-spec})))
  (when-let [cof (:clause-order-fn dialect-spec)]
    (when-not (fn? cof)
      (throw (ex-info "Dialect spec contains :clause-order-fn but it is not a function"
                      {:dialect-spec dialect-spec}))))
  (when-some [as (:as dialect-spec)]
    (when-not (boolean? as)
      (throw (ex-info "Dialect spec contains :as but it is not a boolean"
                      {:dialect-spec dialect-spec}))))
  (swap! dialects assoc dialect (assoc dialect-spec :dialect dialect)))

(defn get-dialect
  "Given a dialect name (keyword), return its definition.
  Returns `nil` if the dialect is unknown."
  [dialect]
  (get @dialects dialect))

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
      (when-not (and f (or (fn? f) (and (var? f) (fn? (deref f)))))
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

;; helper functions to create HoneySQL data structures from other things

(defn map=
  "Given a hash map, return a condition structure that can be used in a
  WHERE clause to test for equality:

  {:select :* :from :table :where (sql/map= {:id 1})}

  will produce: SELECT * FROM table WHERE id = ? (and a parameter of 1)"
  [data]
  (let [clauses (reduce-kv (fn [where col val]
                             (conj where [:= col val]))
                           []
                           data)]
    (if (= 1 (count clauses))
      (first clauses)
      (into [:and] clauses))))

;; aids to migration from HoneySQL 1.x -- these are deliberately undocumented
;; so as not to encourage their use for folks starting fresh with 2.x!

(defn ^:no-doc call [f & args] (apply vector f args))

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
