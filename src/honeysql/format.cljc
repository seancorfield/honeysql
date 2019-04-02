(ns honeysql.format
  (:refer-clojure :exclude [format])
  (:require [honeysql.types :as types
                            :refer [call raw param param-name inline-str
                                    #?@(:cljs [SqlCall SqlRaw SqlParam SqlArray SqlInline])]]
            [clojure.string :as string])
  #?(:clj (:import [honeysql.types SqlCall SqlRaw SqlParam SqlArray SqlInline])))

;;(set! *warn-on-reflection* true)

;;;;

(defn comma-join [s]
  (string/join ", " s))

(defn space-join [s]
  (string/join " " s))

(defn paren-wrap [x]
  (str "(" x ")"))

(def ^:dynamic *clause*
  "During formatting, *clause* is bound to :select, :from, :where, etc."
  nil)

(def ^:dynamic *params*
  "Will be bound to an atom-vector that accumulates SQL parameters across
  possibly-recursive function calls"
  nil)

(def ^:dynamic *param-names* nil)

(def ^:dynamic *param-counter* nil)

(def ^:dynamic *all-param-counter* nil)

(def ^:dynamic *input-params* nil)

(def ^:dynamic *fn-context?* false)

(def ^:dynamic *value-context?* false)

(def ^:dynamic *subquery?* false)

(def ^:dynamic *allow-dashed-names?* false)

(def ^:dynamic *name-transform-fn* nil)

(def ^:private quote-fns
  {:ansi #(str \" (string/replace % "\"" "\"\"") \")
   :mysql #(str \` (string/replace % "`" "``") \`)
   :sqlserver #(str \[ (string/replace % "]" "]]") \])
   :oracle #(str \" (string/replace % "\"" "\"\"") \")})


(defmulti parameterize (fn [parameterizer & args] parameterizer))

(defmethod parameterize :postgresql [_ value pname]
  (str "$" (swap! *all-param-counter* inc)))

(defmethod parameterize :jdbc [_ value pname]
  "?")

(defmethod parameterize :none [_ value pname]
  (str (last @*params*)))


(def ^:dynamic *quote-identifier-fn* nil)
(def ^:dynamic *parameterizer* nil)

(defn- undasherize [s]
  (string/replace s "-" "_"))

;; String.toUpperCase() or `string/upper-case` for that matter converts the string to uppercase for the DEFAULT
;; LOCALE. Normally this does what you'd expect but things like `inner join` get converted to `İNNER JOIN` (dot over
;; the I) when user locale is Turkish. This predictably has bad consequences for people who like their SQL queries to
;; work. The fix here is to use String.toUpperCase(Locale/US) instead which always converts things the way we'd expect.
;;
;; Use this function instead of `string/upper-case` as it will always use Locale/US.
(def ^:private ^{:arglists '([s])} upper-case
  ;; TODO - not sure if there's a JavaScript equivalent here we should be using as well
  #?(:clj (fn [s] (.. s toString (toUpperCase (java.util.Locale/US))))
     :cljs string/upper-case))

(defn quote-identifier [x & {:keys [style split] :or {split true}}]
  (let [name-transform-fn (cond
                            *name-transform-fn* *name-transform-fn*
                            *allow-dashed-names?* identity
                            :else undasherize)
        qf (if style
             (quote-fns style)
             *quote-identifier-fn*)
        s (cond
            (or (keyword? x) (symbol? x))
            (name-transform-fn
              (str (when-let [n (namespace x)]
                     (str n "/")) (name x)))
            (string? x) (if qf x (name-transform-fn x))
            :else (str x))]
    (if-not qf
      s
      (let [qf* #(if (= "*" %) % (qf %))]
        (if-not split
         (qf* s)
         (let [parts (string/split s #"\.")]
           (string/join "." (map qf* parts))))))))

(def infix-fns
  #{"+" "-" "*" "/" "%" "mod" "|" "&" "^"
    "and" "or" "xor"
    "in" "not in" "like" "not like" "regexp"})

(def fn-aliases
  {"is" "="
   "is-not" "<>"
   "not=" "<>"
   "!=" "<>"
   "not-in" "not in"
   "not-like" "not like"
   "regex" "regexp"})

(defprotocol ToSql
  (to-sql [x]))

(defn to-sql-value [x]
  (binding [*value-context?* (sequential? x)]
    (to-sql x)))

(defmulti fn-handler (fn [op & args] op))

(defn expand-binary-ops [op & args]
  (str "("
       (string/join " AND "
                    (for [[a b] (partition 2 1 args)]
                      (fn-handler op a b)))
       ")"))

(defmethod fn-handler :default [op & args]
  (let [args (map to-sql args)]
    (if (infix-fns op)
      (paren-wrap (string/join (str " " op " ") args))
      (str op (paren-wrap (comma-join args))))))

(defmethod fn-handler "count-distinct" [_ & args]
  (str "COUNT(DISTINCT " (comma-join (map to-sql args)) ")"))

(defmethod fn-handler "distinct-on" [_ & args]
  (str "DISTINCT ON (" (comma-join (map to-sql args)) ")"))

(defmethod fn-handler "cast" [_ field cast-to-type]
  (str "CAST" (paren-wrap (str (to-sql field)
                               " AS "
                               (to-sql cast-to-type)))))

(defmethod fn-handler "=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "=" a b more)
    (cond
     (nil? a) (str (to-sql-value b) " IS NULL")
     (nil? b) (str (to-sql-value a) " IS NULL")
     :else (str (to-sql-value a) " = " (to-sql-value b)))))

(defmethod fn-handler "<>" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<>" a b more)
    (cond
     (nil? a) (str (to-sql-value b) " IS NOT NULL")
     (nil? b) (str (to-sql-value a) " IS NOT NULL")
     :else (str (to-sql-value a) " <> " (to-sql-value b)))))

(defmethod fn-handler "<" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<" a b more)
    (str (to-sql-value a) " < " (to-sql-value b))))

(defmethod fn-handler "<=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<=" a b more)
    (str (to-sql-value a) " <= " (to-sql-value b))))

(defmethod fn-handler ">" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">" a b more)
    (str (to-sql-value a) " > " (to-sql-value b))))

(defmethod fn-handler ">=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">=" a b more)
    (str (to-sql-value a) " >= " (to-sql-value b))))

(defmethod fn-handler "between" [_ field lower upper]
  (str (to-sql-value field) " BETWEEN " (to-sql-value lower) " AND " (to-sql-value upper)))

;; Handles MySql's MATCH (field) AGAINST (pattern). The third argument
;; can be a set containing one or more of :boolean, :natural, or :expand.
(defmethod fn-handler "match" [_ fields pattern & [opts]]
  (str "MATCH ("
       (comma-join
        (map to-sql (if (coll? fields) fields [fields])))
       ") AGAINST ("
       (to-sql-value pattern)
       (when (seq opts)
         (str " " (space-join (for [opt opts]
                                (case opt
                                  :boolean "IN BOOLEAN MODE"
                                  :natural "IN NATURAL LANGUAGE MODE"
                                  :expand "WITH QUERY EXPANSION")))))
       ")"))

(def default-clause-priorities
  "Determines the order that clauses will be placed within generated SQL"
  {:with 20
   :with-recursive 30
   :intersect 35
   :union 40
   :union-all 45
   :select 50
   :insert-into 60
   :update 70
   :delete 75
   :delete-from 80
   :columns 90
   :from 110
   :join 120
   :left-join 130
   :right-join 140
   :full-join 150
   :set 155
   :where 160
   :group-by 170
   :having 180
   :order-by 190
   :limit 200
   :offset 210
   :lock 215
   :values 220
   :query-values 230})

(def clause-store (atom default-clause-priorities))

(defn register-clause! [clause-key priority]
  (swap! clause-store assoc clause-key priority))

(defn sort-clauses [clauses]
  (let [m @clause-store]
    (sort-by
      (fn [c]
        (m c #?(:clj Long/MAX_VALUE :cljs js/Number.MAX_VALUE)))
      clauses)))

(defn format
  "Takes a SQL map and optional input parameters and returns a vector
  of a SQL string and parameters, as expected by clojure.java.jdbc.

  Input parameters will be filled into designated spots according to
  name (if a map is provided) or by position (if a sequence is provided).

  Instead of passing parameters, you can use keyword arguments:
    :params - input parameters
    :quoting - quote style to use for identifiers; one of :ansi (PostgreSQL),
               :mysql, :sqlserver, or :oracle. Defaults to no quoting.
    :parameterizer - style of parameter naming, :postgresql,
                     :jdbc or :none. Defaults to :jdbc.
    :return-param-names - when true, returns a vector of
                          [sql-str param-values param-names]"
  [sql-map & params-or-opts]
  (let [opts (when (keyword? (first params-or-opts))
                   (apply hash-map params-or-opts))
        params (if (coll? (first params-or-opts))
                 (first params-or-opts)
                 (:params opts))]
    (binding [*params* (atom [])
              *param-counter* (atom 0)
              *all-param-counter* (atom 0)
              *param-names* (atom [])
              *input-params* (atom params)
              *quote-identifier-fn* (quote-fns (:quoting opts))
              *parameterizer* (or (:parameterizer opts) :jdbc)
              *allow-dashed-names?* (:allow-dashed-names? opts)]
      (let [sql-str (to-sql sql-map)]
        (if (and (seq @*params*) (not= :none (:parameterizer opts)))
          (if (:return-param-names opts)
            [sql-str @*params* @*param-names*]
            (into [sql-str] @*params*))
          [sql-str])))))

(defprotocol Parameterizable
  (to-params [value pname]))

(defn to-params-seq [s pname]
  (paren-wrap (comma-join (mapv #(to-params % pname) s))))

(defn to-params-default [value pname]
  (swap! *params* conj value)
  (swap! *param-names* conj pname)
  (parameterize *parameterizer* value pname))

(extend-protocol Parameterizable
  #?@(:clj
       [clojure.lang.Sequential

        (to-params [value pname]
          (to-params-seq value pname))])
  #?(:clj clojure.lang.IPersistentSet
     :cljs cljs.core/PersistentHashSet)
  (to-params [value pname]
    (to-params (seq value) pname))
  nil
  (to-params [value pname]
    (swap! *params* conj value)
    (swap! *param-names* conj pname)
    (parameterize *parameterizer* value pname))
  #?(:clj Object :cljs default)
  (to-params [value pname]
    #?(:clj
        (to-params-default value pname)
       :cljs
        (if (sequential? value)
          (to-params-seq value pname)
          (to-params-default value pname)))))

(defn add-param [pname pval]
  (to-params pval pname))

;; Anonymous param name -- :_1, :_2, etc.
(defn add-anon-param [pval]
  (add-param
    (keyword (str "_" (swap! *param-counter* inc)))
    pval))

(defrecord Value [v]
  ToSql
  (to-sql [_]
    (add-anon-param v)))

(defn value [x] (Value. x))

(declare -format-clause)

(defn map->sql [m]
  (let [clause-ops (sort-clauses (keys m))
        sql-str (binding [*subquery?* true
                          *fn-context?* false]
                  (space-join
                   (map (comp #(-format-clause % m) #(find m %))
                        clause-ops)))]
    (if *subquery?*
      (paren-wrap sql-str)
      sql-str)))

(declare format-predicate*)

(defn seq->sql [x]
  (cond
    *value-context?*
    ;; sequences are operators/functions
    (format-predicate* x)
    *fn-context?*
    ;; list argument in fn call
    (paren-wrap (comma-join (map to-sql x)))
    :else
    ;; alias
    (do
      (assert (= 2 (count x)) (str "Alias should have two parts" x))
      (let [[target alias] x]
        (str (to-sql target)
             ; Omit AS in FROM, JOIN, etc. - Oracle doesn't allow it
             (if (= :select *clause*) " AS " " ")
             (if (or (string? alias) (keyword? alias) (symbol? alias))
               (quote-identifier alias :split false)
               (to-sql alias)))))))

(extend-protocol types/Inlinable
  #?(:clj clojure.lang.Keyword
     :cljs cljs.core/Keyword)
  (inline-str [x]
    (name x))
  nil
  (inline-str [_]
    "NULL")
  #?(:clj Object :cljs default)
  (inline-str [x]
    (str x)))

(extend-protocol ToSql
  #?(:clj clojure.lang.Keyword
     :cljs cljs.core/Keyword)
  (to-sql [x]
    (let [s (name x)]
      (case (.charAt s 0)
        \% (let [call-args (string/split (subs s 1) #"\." 2)]
             (to-sql (apply call (map keyword call-args))))
        \? (to-sql (param (keyword (subs s 1))))
        (quote-identifier x))))
  #?(:clj clojure.lang.Symbol
     :cljs cljs.core/Symbol)
  (to-sql [x] (quote-identifier x))
  #?(:clj java.lang.Boolean :cljs boolean)
  (to-sql [x]
    (if x "TRUE" "FALSE"))
  #?@(:clj
       [clojure.lang.Sequential
        (to-sql [x] (seq->sql x))])
  SqlCall
  (to-sql [x]
    (binding [*fn-context?* true]
       (let [fn-name (name (.-name x))
             fn-name (fn-aliases fn-name fn-name)]
         (apply fn-handler fn-name (.-args x)))))
  SqlRaw
  (to-sql [x]
    (let [s (.-s x)]
      (if (vector? s)
        (string/join "" (map (fn [x] (if (string? x) x (to-sql x))) s))
        s)))
  #?(:clj clojure.lang.IPersistentMap
     :cljs cljs.core/PersistentArrayMap)
  (to-sql [x]
    (map->sql x))
  #?(:clj clojure.lang.IPersistentSet
     :cljs cljs.core/PersistentHashSet)
  (to-sql [x]
    (to-sql (seq x)))
  nil
  (to-sql [x] "NULL")
  SqlParam
  (to-sql [x]
    (let [pname (param-name x)]
      (if (map? @*input-params*)
        (add-param pname (get @*input-params* pname))
        (let [x (first @*input-params*)]
          (swap! *input-params* rest)
          (add-param pname x)))))
  SqlArray
  (to-sql [x]
    (str "ARRAY[" (comma-join (map to-sql (.-values x))) "]"))
  SqlInline
  (to-sql [x]
    (inline-str (.-value x)))
  #?(:clj Object :cljs default)
  (to-sql [x]
    #?(:clj (add-anon-param x)
       :cljs (if (sequential? x)
               (seq->sql x)
               (add-anon-param x))))
  #?@(:cljs
       [cljs.core/PersistentHashMap
        (to-sql [x] (map->sql x))]))

(defn sqlable? [x]
  (satisfies? ToSql x))

;;;;

(defn format-predicate* [pred]
  (if-not (sequential? pred)
    (to-sql pred)
    (let [[op & args] pred
          op-name (name op)]
      (case op-name
        "not" (str "NOT " (format-predicate* (first args)))

        ("and" "or" "xor")
        (->> args
             (remove nil?)
             (map format-predicate*)
             (string/join (str " " (upper-case op-name) " "))
             (paren-wrap))

        "exists"
        (str "EXISTS " (to-sql (first args)))

        (to-sql (apply call pred))))))

(defn format-predicate
  "Formats a predicate (e.g., for WHERE, JOIN, or HAVING) as a string."
  [pred & {:keys [quoting parameterizer]
           :or {parameterizer :jdbc}}]
  (binding [*params* (atom [])
            *param-counter* (atom 0)
            *param-names* (atom [])
            *quote-identifier-fn* (or (quote-fns quoting)
                                      *quote-identifier-fn*)
            *parameterizer* parameterizer]
    (let [sql-str (format-predicate* pred)]
      (if (seq @*params*)
        (into [sql-str] @*params*)
        [sql-str]))))

(defmulti format-clause
  "Takes a map entry representing a clause and returns an SQL string"
  (fn [clause _] (key clause)))

(defn- -format-clause
  [clause _]
  (binding [*clause* (key clause)]
    (format-clause clause _)))

(defmethod format-clause :default [& _]
  "")

(defmethod format-clause :exists [[_ table-expr] _]
  (str "EXISTS " (to-sql table-expr)))

(defmethod format-clause :select [[_ fields] sql-map]
  (str "SELECT "
       (when (:modifiers sql-map)
         (str (space-join (map (comp upper-case name)
                               (:modifiers sql-map)))
              " "))
       (comma-join (map to-sql fields))))

(defmethod format-clause :from [[_ tables] _]
  (str "FROM " (comma-join (map to-sql tables))))

(defmethod format-clause :where [[_ pred] _]
  (str "WHERE " (format-predicate* pred)))

(defn format-join [type table pred]
  (str (when type
         (str (upper-case (name type)) " "))
       "JOIN " (to-sql table)
       (when pred
         (if (= :using (first pred))
           (str " USING (" (->> pred rest (map quote-identifier) comma-join) ")")
           (str " ON " (format-predicate* pred))))))

(defmethod format-clause :join [[_ join-groups] _]
  (space-join (map #(apply format-join :inner %)
                   (partition 2 join-groups))))

(defmethod format-clause :left-join [[_ join-groups] _]
  (space-join (map #(apply format-join :left %)
                   (partition 2 join-groups))))

(defmethod format-clause :right-join [[_ join-groups] _]
  (space-join (map #(apply format-join :right %)
                   (partition 2 join-groups))))

(defmethod format-clause :full-join [[_ join-groups] _]
  (space-join (map #(apply format-join :full %)
                   (partition 2 join-groups))))

(defmethod format-clause :group-by [[_ fields] _]
  (str "GROUP BY " (comma-join (map to-sql fields))))

(defmethod format-clause :having [[_ pred] _]
  (str "HAVING " (format-predicate* pred)))

(defmethod format-clause :order-by [[_ fields] _]
  (str "ORDER BY "
       (comma-join (for [field fields]
                     (if (sequential? field)
                       (let [[field & modifiers] field]
                         (string/join " "
                                      (cons (to-sql field)
                                            (for [modifier modifiers]
                                              (case modifier
                                                :desc "DESC"
                                                :asc "ASC"
                                                :nulls-first "NULLS FIRST"
                                                :nulls-last "NULLS LAST"
                                                "")))))
                       (to-sql field))))))

(defmethod format-clause :limit [[_ limit] _]
  (str "LIMIT " (to-sql limit)))

(defmethod format-clause :offset [[_ offset] _]
  (str "OFFSET " (to-sql offset)))

(defmulti format-lock-clause identity)

(defmethod format-lock-clause :update [_]
  "FOR UPDATE")

(defmethod format-lock-clause :mysql-share [_]
  "LOCK IN SHARE MODE")

(defmethod format-lock-clause :postgresql-share [_]
  "FOR SHARE")

(defmethod format-clause :lock [[_ lock] _]
  (let [{:keys [mode wait]} lock
        clause (format-lock-clause mode)]
    (str clause (when (false? wait) " NOWAIT"))))

(defmethod format-clause :insert-into [[_ table] _]
  (if (and (sequential? table) (sequential? (first table)))
    (str "INSERT INTO "
         (to-sql (ffirst table))
         " (" (comma-join (map to-sql (second (first table)))) ") "
         (binding [*subquery?* false]
           (to-sql (second table))))
    (str "INSERT INTO " (to-sql table))))

(defmethod format-clause :columns [[_ fields] _]
  (str "(" (comma-join (map to-sql fields)) ")"))

(defmethod format-clause :values [[_ values] _]
  (if (sequential? (first values))
    (str "VALUES " (comma-join (for [x values]
                                 (str "(" (comma-join (map to-sql x)) ")"))))
    (let [cols (keys (first values))]
      (str
       "(" (comma-join (map to-sql cols)) ") VALUES "
       (comma-join (for [x values]
                     (str "(" (comma-join (map #(to-sql (get x %)) cols)) ")")))))))

(defmethod format-clause :query-values [[_ query-values] _]
  (to-sql query-values))

(defmethod format-clause :update [[_ table] _]
  (str "UPDATE " (to-sql table)))

(defmethod format-clause :set [[_ values] _]
  (str "SET " (comma-join (for [[k v] values]
                            (str (to-sql k) " = " (to-sql v))))))

(defmethod format-clause :delete-from [[_ table] _]
  (str "DELETE FROM " (to-sql table)))

(defmethod format-clause :delete [[_ tables] _]
  (str "DELETE " (comma-join (map to-sql tables))))

(defn cte->sql
  [[cte-name query]]
  (str (binding [*subquery?* false]
         (to-sql cte-name))
       " AS "
       (to-sql query)))

(defmethod format-clause :with [[_ ctes] _]
  (str "WITH " (comma-join (map cte->sql ctes))))

(defmethod format-clause :with-recursive [[_ ctes] _]
  (str "WITH RECURSIVE " (comma-join (map cte->sql ctes))))

(defmethod format-clause :union [[_ maps] _]
  (binding [*subquery?* false]
    (string/join " UNION " (map to-sql maps))))

(defmethod format-clause :union-all [[_ maps] _]
  (binding [*subquery?* false]
    (string/join " UNION ALL " (map to-sql maps))))

(defmethod format-clause :intersect [[_ maps] _]
  (binding [*subquery?* false]
    (string/join " INTERSECT " (map to-sql maps))))

(defmethod fn-handler "case" [_ & clauses]
  (str "CASE "
       (space-join
        (for [[condition result] (partition 2 clauses)]
          (if (= :else condition)
            (str "ELSE " (to-sql result))
            (let [pred (format-predicate* condition)]
              (str "WHEN " pred " THEN " (to-sql result))))))
       " END"))

(defn regularize [sql-string]
  (string/replace sql-string #"\s+" " "))
