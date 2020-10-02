;; copyright (c) 2020 sean corfield, all rights reserved

(ns honey.sql
  "Primary API for HoneySQL 2.x."
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
  [:with :with-recursive :intersect :union :union-all :except :except-all
   :select :insert-into :update :delete :delete-from :truncate
   :columns :composite :set :from
   :join :left-join :right-join :inner-join :outer-join :full-join
   :cross-join
   :where :group-by :having :order-by :limit :offset :for :values
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

(defn- sql-kw [k]
  (-> k (name) (upper-case) (str/replace "-" " ")))

(defn- format-entity [x & [{:keys [aliased? drop-ns?]}]]
  (let [q     (if *quoted* (:quote *dialect*) identity)
        [t c] (if-let [n (when-not (or drop-ns? (string? x))
                           (namespace x))]
                [n (name x)]
                (if aliased?
                  [nil (name x)]
                  (let [[t c] (str/split (name x) #"\.")]
                    (if c [t c] [nil t]))))]
    (cond->> c
      (not= "*" c)
      (q)
      t
      (str (q t) "."))))

(defn- ->param [k]
  (with-meta (constantly k)
    {::wrapper
     (fn [fk {:keys [params]}]
       (let [k (fk)]
         (if (contains? params k)
           (get params k)
           (throw (ex-info (str "missing parameter value for " k)
                           {:params (keys params)})))))}))

(defn- format-var [x & [opts]]
  (let [c (name x)]
    (cond (= \% (first c))
          (let [[f & args] (str/split (subs c 1) #"\.")]
            ;; TODO: this does not quote arguments -- does that matter?
            [(str f "(" (str/join "," args) ")")])
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
                 (format-entity (second x) {:aliased? true}))))

        :else
        (format-entity x)))

(defn- format-selectable-dsl [x & [{:keys [as? aliased?] :as opts}]]
  (cond (map? x)
        (format-dsl x {:nested? true})

        (sequential? x)
        (let [s     (first x)
              pair? (< 1 (count x))
              a     (second x)
              [sql & params] (if (map? s)
                               (format-dsl s {:nested? true})
                               (format-expr s))
              [sql' & params'] (when pair?
                                 (if (sequential? a)
                                   (let [[sql params] (format-expr-list a {:aliased? true})]
                                     (into [(str/join " " sql)] params))
                                   (format-selectable-dsl a {:aliased? true})))]
          (-> [(cond-> sql
                 pair?
                 (str (if as?
                        (if (and (contains? *dialect* :as)
                                 (not (:as *dialect*)))
                          " "
                          " AS ")
                        " ") sql'))]
              (into params)
              (into params')))

        (or (keyword? x) (symbol? x))
        (if aliased?
          [(format-entity x opts)]
          (format-var x opts))

        (and aliased? (string? x))
        [(format-entity x opts)]

        :else
        (format-expr x)))

;; primary clauses

(defn- format-on-set-op [k xs]
  (let [[sqls params]
        (reduce (fn [[sql params] [sql' & params']]
                  [(conj sql sql') (if params' (into params params') params)])
                [[] []]
                (map #'format-dsl xs))]
    (into [(str/join (str " " (sql-kw k) " ") sqls)] params)))

(defn- format-expr-list [xs & [opts]]
  (reduce (fn [[sql params] [sql' & params']]
            [(conj sql sql') (if params' (into params params') params)])
          [[] []]
          (map #(format-expr % opts) xs)))

(defn- format-columns [k xs]
  (let [[sqls params] (format-expr-list xs {:drop-ns? (= :columns k)})]
    (into [(str "(" (str/join ", " sqls) ")")] params)))

(defn- format-selects [k xs]
  (if (sequential? xs)
    (let [[sqls params]
          (reduce (fn [[sql params] [sql' & params']]
                    [(conj sql sql') (if params' (into params params') params)])
                  [[] []]
                  (map #(format-selectable-dsl % {:as? (#{:select :from} k)}) xs))]
      (into [(str (sql-kw k) " " (str/join ", " sqls))] params))
    (let [[sql & params] (format-selectable-dsl xs {:as? (#{:select :from} k)})]
      (into [(str (sql-kw k) " " sql)] params))))

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
    (cond (sequential? (first table))
          (let [[[table cols] statement] table
                [sql & params] (format-dsl statement)]
            (into [(str (sql-kw k) " " (format-entity-alias table)
                        " ("
                        (str/join ", " (map #'format-entity-alias cols))
                        ") "
                        sql)]
                  params))
          (map? (second table))
          (let [[table statement] table
                [sql & params] (format-dsl statement)]
            (into [(str (sql-kw k) " " (format-entity-alias table)
                        " " sql)]
                  params))
          :else
          [(str (sql-kw k) " " (format-entity-alias table))])
    [(str (sql-kw k) " " (format-entity-alias table))]))

(defn- format-join [k [j e]]
  (let [[sql & params] (format-expr e)]
    ;; for backward compatibility, treat plain JOIN as INNER JOIN:
    (into [(str (sql-kw (if (= :join k) :inner-join k)) " "
                (format-entity-alias j) " ON "
                sql)]
          params)))

(defn- format-on-expr [k e]
  (let [[sql & params] (format-expr e)]
    (into [(str (sql-kw k) " " sql)] params)))

(defn- format-group-by [k xs]
  (let [[sqls params] (format-expr-list xs)]
    (into [(str (sql-kw k) " " (str/join ", " sqls))] params)))

(defn- format-order-by [k xs]
  (let [dirs (map #(if (sequential? %) (second %) :asc) xs)
        [sqls params]
        (format-expr-list (map #(if (sequential? %) (first %) %) xs))]
    (into [(str (sql-kw k) " "
                (str/join ", " (map (fn [sql dir] (str sql " " (sql-kw dir)))
                                    sqls
                                    dirs)))] params)))

(defn- format-lock-strength [k xs]
  (let [[strength tables nowait] (if (sequential? xs) xs [xs])]
    [(str (sql-kw k) " " (sql-kw strength)
          (when tables
            (str
              (cond (= :nowait tables)
                    (str " NOWAIT")
                    (sequential? tables)
                    (str " OF "
                         (str/join ", " (map #'format-entity tables)))
                    :else
                    (str " OF " (format-entity tables)))
              (when nowait
                (str " NOWAIT")))))]))

(defn- format-values [k xs]
  (cond (sequential? (first xs))
        ;; [[1 2 3] [4 5 6]]
        (let [[sqls params]
              (reduce (fn [[sql params] [sqls' params']]
                        [(conj sql (str "(" (str/join ", " sqls') ")"))
                         (into params params')])
                      [[] []]
                      (map #'format-expr-list xs))]
          (into [(str (sql-kw k) " " (str/join ", " sqls))] params))

        (map? (first xs))
        ;; [{:a 1 :b 2 :c 3}]
        (let [cols (keys (first xs))
              [sqls params]
              (reduce (fn [[sql params] [sqls' params']]
                        [(conj sql (str/join ", " sqls'))
                         (if params' (into params params') params')])
                      [[] []]
                      (map (fn [m]
                             (format-expr-list (map #(get m %) cols)))
                           xs))]
          (into [(str "("
                      (str/join ", "
                                (map #(format-entity % {:drop-ns? true}) cols))
                      ") "
                      (sql-kw k) " (" (str/join ", " sqls) ")")]
                params))

        :else
        (throw (ex-info ":values expects sequences or maps"
                        {:first (first xs)}))))

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
  (if (or (keyword? x) (symbol? x))
    [(str (sql-kw k) " (" (format-entity x) ")")]
    (let [[sql & params] (format-dsl x)]
      (into [(str (sql-kw k) " " sql)] params))))

(defn- format-do-update-set [k x]
  (if (or (keyword? x) (symbol? x))
    (let [e (format-entity x {:drop-ns? true})]
      [(str (sql-kw k) " " e " = EXCLUDED." e)])
    (format-set-exprs k x)))

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
  (atom {:with           #'format-with
         :with-recursive #'format-with
         :intersect      #'format-on-set-op
         :union          #'format-on-set-op
         :union-all      #'format-on-set-op
         :except         #'format-on-set-op
         :except-all     #'format-on-set-op
         :select         #'format-selects
         :insert-into    #'format-insert
         :update         #'format-selector
         :delete         #'format-selects
         :delete-from    #'format-selector
         :truncate       #'format-selector
         :columns        #'format-columns
         :composite      #'format-columns
         :set            #'format-set-exprs
         :from           #'format-selects
         :join           #'format-join
         :left-join      #'format-join
         :right-join     #'format-join
         :inner-join     #'format-join
         :outer-join     #'format-join
         :full-join      #'format-join
         :cross-join     #'format-selects
         :where          #'format-on-expr
         :group-by       #'format-group-by
         :having         #'format-on-expr
         :order-by       #'format-order-by
         :limit          #'format-on-expr
         :offset         #'format-on-expr
         :for            #'format-lock-strength
         :values         #'format-values
         :on-conflict    #'format-on-conflict
         :on-constraint  #'format-selector
         :do-nothing     (fn [k _] (vector (sql-kw k)))
         :do-update-set  #'format-do-update-set
         :returning      #'format-selects}))

(assert (= (set @base-clause-order)
           (set @current-clause-order)
           (set (keys @clause-format))))

(comment :target
  {;:with 20
   ;:with-recursive 30
   ;:intersect 35
   ;:union 40
   ;:union-all 45
   ;:except 47
   ;:select 50
   ;:insert-into 60
   ;:update 70
   ;:delete 75
   ;:delete-from 80
   ;:truncate 85
   ;:columns 90
   ;:composite 95
   ;; no longer needed/supported :set0 100 ; low-priority set clause
   ;:from 110
   ;:join 120
   ;:left-join 130
   ;:right-join 140
   ;:full-join 150
   ;:cross-join 152 ; doesn't have on clauses
   ;:set 155
   ;; no longer needed/supported :set1 156 ; high-priority set clause (synonym for :set)
   ;:where 160
   ;:group-by 170
   ;:having 180
   ;:order-by 190
   ;:limit 200
   ;:offset 210
   ;:lock 215
   ;:values 220
   :query-values 230})
;; :on-conflict -- https://www.postgresqltutorial.com/postgresql-upsert/
;; :on-constraint (part of :on-conflict)
;; :do-update-set
;; ;do-nothing
   ;:returning

(defn- format-dsl [x & [{:keys [aliased? nested? pretty?]}]]
  (let [[sqls params leftover]
        (reduce (fn [[sql params leftover] k]
                  (if-let [xs (or (k x) (let [s (symbol (name k))] (get x s)))]
                    (let [formatter (k @clause-format)
                          [sql' & params'] (formatter k xs)]
                      [(conj sql sql')
                       (if params' (into params params') params)
                       (dissoc leftover k (symbol (name k)))])
                    [sql params leftover]))
                [[] [] x]
                *clause-order*)]
    (if (seq leftover)
      (do
        ;; TODO: for testing purposes, make this less noisy
        (println (str "\n-------------------\nUnknown SQL clauses: "
                      (str/join ", " (keys leftover))))
        #_(throw (ex-info (str "Unknown SQL clauses: "
                               (str/join ", " (keys leftover)))
                          leftover))
        [(str "<unknown" (str/join (keys leftover)) ">")])
      (into [(cond-> (str/join (if pretty? "\n" " ") sqls)
               pretty?
               (as-> s (str "\n" s "\n"))
               (and nested? (not aliased?))
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
        "in" "not-in" "like" "not-like" "regexp"
        "ilike" "not-ilike"
        "is" "is-not" "not=" "!=" "regex"}
      (into (map str "+-*/%|&^=<>"))
      (into (keys infix-aliases))
      (into (vals infix-aliases))
      (->> (into #{} (map keyword)))
      (atom)))

(def ^:private op-ignore-nil (atom #{:and :or}))
(def ^:private op-variadic   (atom #{:and :or :+ :* :||}))

(defn- sqlize-value [x]
  (cond
    (nil? x)     "NULL"
    (string? x)  (str \' (str/replace x "'" "''") \')
    (symbol? x)  (name x)
    (keyword? x) (name x)
    :else        (str x)))

(def ^:private special-syntax
  (atom
   {:array
    (fn [_ [arr]]
      (let [[sqls params] (format-expr-list arr)]
        (into [(str "ARRAY[" (str/join ", " sqls) "]")] params)))
    :between
    (fn [_ [x a b]]
      (let [[sql-x & params-x] (format-expr x {:nested? true})
            [sql-a & params-a] (format-expr a {:nested? true})
            [sql-b & params-b] (format-expr b {:nested? true})]
        (-> [(str sql-x " BETWEEN " sql-a " AND " sql-b)]
            (into params-x)
            (into params-a)
            (into params-b))))
    :cast
    (fn [_ [x type]]
      (let [[sql & params] (format-expr x)]
        (into [(str "CAST(" sql " AS " (sql-kw type) ")")] params)))
    :default
    (fn [_ []]
      ["DEFAULT"])
    :inline
    (fn [_ [x]]
      [(sqlize-value x)])
    :interval
    (fn [_ [n units]]
      (let [[sql & params] (format-expr n)]
        (into [(str "INTERVAL " sql " " (sql-kw units))] params)))
    :not
    (fn [_ [x]]
      (let [[sql & params] (format-expr x)]
        (into [(str "NOT " sql)] params)))
    :param
    (fn [_ [k]]
      ["?" (->param k)])
    :raw
    ;; TODO: only supports single raw string right now
    (fn [_ [s]]
      [s])}))

(defn format-expr [x & [{:keys [nested?] :as opts}]]
  (cond (or (keyword? x) (symbol? x))
        (format-var x opts)

        (map? x)
        (format-dsl x (assoc opts :nested? true))

        (sequential? x)
        (let [op (first x)
              ;; normalize symbols to keywords here -- makes the subsequent
              ;; logic easier since we use op to lookup things in hash maps:
              op (if (symbol? op) (keyword (name op)) op)]
          (if (keyword? op)
            (cond (contains? @infix-ops op)
                  (if (contains? @op-variadic op) ; no aliases here, no special semantics
                    (let [x (if (contains? @op-ignore-nil op) (remove nil? x) x)
                          [sqls params]
                          (reduce (fn [[sql params] [sql' & params']]
                                    [(conj sql sql')
                                     (if params' (into params params') params)])
                                  [[] []]
                                  (map #(format-expr % {:nested? true})
                                       (rest x)))]
                      (into [(cond-> (str/join (str " " (sql-kw op) " ") sqls)
                               nested?
                               (as-> s (str "(" s ")")))]
                            params))
                    (let [[_ a b & y] x
                          _           (when (seq y)
                                        (throw (ex-info (str "only binary "
                                                             op
                                                             "is supported")
                                                        {:expr x})))
                          [s1 & p1]   (format-expr a {:nested? true})
                          [s2 & p2]   (format-expr b {:nested? true})
                          op          (get infix-aliases op op)]
                        (if (and (#{:= :<>} op) (or (nil? a) (nil? b)))
                          (-> (str (if (nil? a)
                                     (if (nil? b) "NULL" s2)
                                     s1)
                                   (if (= := op) " IS NULL" " IS NOT NULL"))
                              (cond-> nested?
                                (as-> s (str "(" s ")")))
                              (vector))
                          (-> (str s1 " " (sql-kw op) " " s2)
                              (cond-> nested?
                                (as-> s (str "(" s ")")))
                              (vector)
                              (into p1)
                              (into p2)))))
                  (contains? @special-syntax op)
                  (let [formatter (get @special-syntax op)]
                    (formatter op (rest x)))
                  :else
                  (let [args          (rest x)
                        [sqls params] (format-expr-list args)]
                    (into [(str (sql-kw op)
                                (if (and (= 1 (count args))
                                         (map? (first args))
                                         (= 1 (count sqls)))
                                  (str " " (first sqls))
                                  (str "(" (str/join ", " sqls) ")")))]
                          params)))
            (into [(str "(" (str/join ", "
                                      (repeat (count x) "?")) ")")]
                  x)))

        (or (true? x) (false? x)) ; because (boolean? x) requires Clojure 1.9+
        [(upper-case (str x))]

        (nil? x)
        ["NULL"]

        :else
        ["?" x]))

(defn- check-dialect [dialect]
  (when-not (contains? dialects dialect)
    (throw (ex-info (str "Invalid dialect: " dialect)
                    {:valid-dialects (vec (sort (keys dialects)))})))
  dialect)

(defn- unwrap [x opts]
  (if-let [m (meta x)]
    (if-let [f (::wrapper m)]
      (f x opts)
      x)
    x))

(defn format
  "Turn the data DSL into a vector containing a SQL string followed by
  any parameter values that were encountered in the DSL structure."
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
               *quoted*  (if (contains? opts :quoted)
                           (:quoted opts)
                           dialect?)]
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
  (assert (keyword? clause))
  (let [f (if (keyword? formatter)
            (get @clause-format formatter)
            formatter)]
    (when-not (and f (fn? f))
      (throw (ex-info "The formatter must be a function or existing clause"
                      {:type (type formatter)})))
    (swap! base-clause-order add-clause-before clause before)
    (swap! current-clause-order add-clause-before clause before)
    (swap! clause-format assoc clause f)))

(defn register-fn!
  "Register a new function (as special syntax). The `formatter` is either
  a keyword, meaning that this new function should use the same syntax as
  an existing function, or a function of two arguments that generates a
  SQL string and parameters (as a vector). The two arguments are the name
  of the function (as a keyword) and a sequence of the arguments from the
  DSL."
  [function formatter]
  (assert (keyword? function))
  (let [f (if (keyword? formatter)
            (get @special-syntax formatter)
            formatter)]
    (when-not (and f (fn? f))
      (throw (ex-info "The formatter must be a function or existing fn name"
                      {:type (type formatter)})))
    (swap! special-syntax assoc function f)))

(defn register-op!
  "Register a new infix operator. Operators can be defined to be variadic (the
  default is that they are binary) and may choose to ignore `nil` arguments
  (this can make it easier to programmatically construct the DSL)."
  [op & {:keys [variadic? ignore-nil?]}]
  (assert (keyword? op))
  (swap! infix-ops conj op)
  (when variadic?
    (swap! op-variadic conj op))
  (when ignore-nil?
    (swap! op-ignore-nil conj op)))

(comment
  (format {:truncate :foo})
  (format-expr [:= :id 1])
  (format-expr [:+ :id 1])
  (format-expr [:+ 1 [:+ 1 :quux]])
  (format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])
  (format-expr :id)
  (format-expr 1)
  (format {:select [:a [:b :c] [[:d :e]] [[:f :g] :h]]})
  (format-on-expr :where [:= :id 1])
  (format-dsl {:select [:*] :from [:table] :where [:= :id 1]})
  (format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]} {})
  (format {:select [:*] :from [:table] :group-by [:foo :bar]} {})
  (format {:select [:*] :from [:table] :group-by [[:date :bar]]} {})
  (format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]} {})
  (format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]} {})
  (println (format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]} {:pretty? true}))
  (format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]} {})
  (format-expr [:interval 30 :days])
  (format {:select [:*] :from [:table] :where [:= :id (int 1)]} {:dialect :mysql})
  (map fn? (format {:select [:*] :from [:table] :where [:= :id (with-meta (constantly 42) {:foo true})]} {:dialect :mysql}))
  (println (format {:select [:*] :from [:table] :where [:in :id [1 2 3 4]]} {:pretty? true}))
  ,)
