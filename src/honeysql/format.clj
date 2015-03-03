(ns honeysql.format
  (:refer-clojure :exclude [format])
  (:require [honeysql.types :refer [call raw param param-name]]
            [clojure.string :as string])
  (:import [honeysql.types SqlCall SqlRaw SqlParam]))

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

(def ^:dynamic *input-params* nil)

(def ^:dynamic *fn-context?* false)

(def ^:dynamic *subquery?* false)

(def ^:private quote-fns
  {:ansi #(str \" % \")
   :mysql #(str \` % \`)
   :sqlserver #(str \[ % \])
   :oracle #(str \" % \")})

(def ^:dynamic *quote-identifier-fn* nil)

(defn- undasherize [s]
  (string/replace s "-" "_"))
(defn quote-identifier [x & {:keys [style split] :or {split true}}]
  (let [qf (if style
             (quote-fns style)
             *quote-identifier-fn*)
        s (cond
            (or (keyword? x) (symbol? x)) (undasherize (name x))
            (string? x) (if qf x (undasherize x))
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

(declare to-sql format-predicate*)

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

(defmethod fn-handler "=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "=" a b more)
    (cond
     (nil? a) (str (to-sql b) " IS NULL")
     (nil? b) (str (to-sql a) " IS NULL")
     :else (str (to-sql a) " = " (to-sql b)))))

(defmethod fn-handler "<>" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<>" a b more)
    (cond
     (nil? a) (str (to-sql b) " IS NOT NULL")
     (nil? b) (str (to-sql a) " IS NOT NULL")
     :else (str (to-sql a) " <> " (to-sql b)))))

(defmethod fn-handler "<" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<" a b more)
    (str (to-sql a) " < " (to-sql b))))

(defmethod fn-handler "<=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<=" a b more)
    (str (to-sql a) " <= " (to-sql b))))

(defmethod fn-handler ">" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">" a b more)
    (str (to-sql a) " > " (to-sql b))))

(defmethod fn-handler ">=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">=" a b more)
    (str (to-sql a) " >= " (to-sql b))))

(defmethod fn-handler "between" [_ field lower upper]
  (str (to-sql field) " BETWEEN " (to-sql lower) " AND " (to-sql upper)))

;; Handles MySql's MATCH (field) AGAINST (pattern). The third argument
;; can be a set containing one or more of :boolean, :natural, or :expand.
(defmethod fn-handler "match" [_ fields pattern & [opts]]
  (str "MATCH ("
       (comma-join
        (map to-sql (if (coll? fields) fields [fields])))
       ") AGAINST ("
       (to-sql pattern)
       (when (seq opts)
         (str " " (space-join (for [opt opts]
                                (condp = opt
                                  :boolean "IN BOOLEAN MODE"
                                  :natural "IN NATURAL LANGUAGE MODE"
                                  :expand "WITH QUERY EXPANSION")))))
       ")"))

(def clause-order
  "Determines the order that clauses will be placed within generated SQL"
  [:select :insert-into :update :delete-from :columns :set :from :join
   :left-join :right-join :where :aggregate :over :partition-by :group-by
   :having :order-by :limit :offset :values :query-values])

(def known-clauses (set clause-order))

(defn format
  "Takes a SQL map and optional input parameters and returns a vector
  of a SQL string and parameters, as expected by clojure.java.jdbc.

  Input parameters will be filled into designated spots according to
  name (if a map is provided) or by position (if a sequence is provided).

  Instead of passing parameters, you can use keyword arguments:
    :params - input parameters
    :quoting - quote style to use for identifiers; one of :ansi (PostgreSQL),
               :mysql, :sqlserver, or :oracle. Defaults to no quoting.
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
              *param-names* (atom [])
              *input-params* (atom params)
              *quote-identifier-fn* (quote-fns (:quoting opts))]
      (let [sql-str (to-sql sql-map)]
        (if (seq @*params*)
          (if (:return-param-names opts)
            [sql-str @*params* @*param-names*]
            (into [sql-str] @*params*))
          [sql-str])))))

(defn format-predicate
  "Formats a predicate (e.g., for WHERE, JOIN, or HAVING) as a string."
  [pred & {:keys [quoting]}]
  (binding [*params* (atom [])
            *param-counter* (atom 0)
            *param-names* (atom [])
            *quote-identifier-fn* (or (quote-fns quoting)
                                      *quote-identifier-fn*)]
    (let [sql-str (format-predicate* pred)]
      (if (seq @*params*)
        (into [sql-str] @*params*)
        [sql-str]))))

(defprotocol ToSql
  (-to-sql [x]))

(declare -format-clause)

(extend-protocol ToSql
  clojure.lang.Keyword
  (-to-sql [x] (let [s ^String (name x)]
                 (condp = (.charAt s 0)
                   \% (let [call-args (string/split (subs s 1) #"\." 2)]
                        (to-sql (apply call (map keyword call-args))))
                   \? (to-sql (param (keyword (subs s 1))))
                   (quote-identifier x))))
  clojure.lang.Symbol
  (-to-sql [x] (quote-identifier x))
  java.lang.Number
  (-to-sql [x] (str x))
  java.lang.Boolean
  (-to-sql [x] (if x "TRUE" "FALSE"))
  clojure.lang.Sequential
  (-to-sql [x] (if *fn-context?*
                 ;; list argument in fn call
                 (paren-wrap (comma-join (map to-sql x)))
                 ;; alias
                 (str (to-sql (first x))
                      ; Omit AS in FROM, JOIN, etc. - Oracle doesn't allow it
                      (if (= :select *clause*)
                        " AS "
                        " ")
                      (if (string? (second x))
                        (quote-identifier (second x))
                        (to-sql (second x))))))
  SqlCall
  (-to-sql [x] (binding [*fn-context?* true]
                 (let [fn-name (name (.name x))
                       fn-name (fn-aliases fn-name fn-name)]
                   (apply fn-handler fn-name (.args x)))))
  SqlRaw
  (-to-sql [x] (.s x))
  clojure.lang.IPersistentMap
  (-to-sql [x] (let [clause-ops (concat
                                 (filter #(contains? x %) clause-order)
                                 (remove known-clauses (keys x)))
                     sql-str (binding [*subquery?* true
                                       *fn-context?* false]
                               (space-join
                                (map (comp #(-format-clause % x) #(find x %))
                                     clause-ops)))]
                 (if *subquery?*
                   (paren-wrap sql-str)
                   sql-str)))
  nil
  (-to-sql [x] "NULL")
  Object
  (-to-sql [x] (let [[x pname] (if (instance? SqlParam x)
                                 (let [pname (param-name x)]
                                   (if (map? @*input-params*)
                                     [(get @*input-params* pname) pname]
                                     (let [x (first @*input-params*)]
                                       (swap! *input-params* rest)
                                       [x pname])))
                                 ;; Anonymous param name -- :_1, :_2, etc.
                                 [x (keyword (str "_" (swap! *param-counter* inc)))])]
                 (swap! *param-names* conj pname)
                 (swap! *params* conj x)
                 "?")))

(defn sqlable? [x]
  (satisfies? ToSql x))

(defn to-sql [x]
  (-to-sql x))

;;;;

(defn format-predicate* [pred]
  (if-not (sequential? pred)
    (to-sql pred)
    (let [[op & args] pred
          op-name (name op)]
      (if (= "not" op-name)
        (str "NOT " (format-predicate* (first args)))
        (if (#{"and" "or" "xor"} op-name)
          (paren-wrap
           (string/join (str " " (string/upper-case op-name) " ")
                        (map format-predicate* args)))
          (to-sql (apply call pred)))))))

(defmulti format-clause
  "Takes a map entry representing a clause and returns an SQL string"
  (fn [clause _] (key clause)))

(defn- -format-clause
  [clause _]
  (binding [*clause* (key clause)]
    (format-clause clause _)))

(defmethod format-clause :default [& _]
  "")

(defmethod format-clause :select [[_ fields] sql-map]
  (str "SELECT "
       (when (:modifiers sql-map)
         (str (space-join (map (comp string/upper-case name)
                               (:modifiers sql-map)))
              " "))
       (comma-join (map to-sql fields))))

(defmethod format-clause :from [[_ tables] _]
  (str "FROM " (comma-join (map to-sql tables))))

(defmethod format-clause :where [[_ pred] _]
  (str "WHERE " (format-predicate* pred)))

(defn format-join [type table pred]
  (str (when type
         (str (string/upper-case (name type)) " "))
       "JOIN " (to-sql table)
       " ON " (format-predicate* pred)))

(defmethod format-clause :join [[_ join-groups] _]
  (space-join (map #(apply format-join :inner %)
                   (partition 2 join-groups))))

(defmethod format-clause :left-join [[_ join-groups] _]
  (space-join (map #(apply format-join :left %)
                   (partition 2 join-groups))))

(defmethod format-clause :right-join [[_ join-groups] _]
  (space-join (map #(apply format-join :right %)
                   (partition 2 join-groups))))

(defmethod format-clause :group-by [[_ fields] _]
  (str "GROUP BY " (comma-join (map to-sql fields))))

(defmethod format-clause :having [[_ pred] _]
  (str "HAVING " (format-predicate* pred)))

(defmethod format-clause :order-by [[_ fields] _]
  (str "ORDER BY "
       (comma-join (for [field fields]
                     (if (sequential? field)
                       (let [[field order] field]
                         (str (to-sql field) " " (if (= :desc order)
                                                   "DESC" "ASC")))
                       (to-sql field))))))

(defmethod format-clause :limit [[_ limit] _]
  (str "LIMIT " (to-sql limit)))

(defmethod format-clause :offset [[_ offset] _]
  (str "OFFSET " (to-sql offset)))

(defmethod format-clause :insert-into [[_ table] _]
  (str "INSERT INTO " (to-sql table)))

(defmethod format-clause :columns [[_ fields] _]
  (str "(" (comma-join (map to-sql fields)) ")"))

(defmethod format-clause :values [[_ values] _]
  (if (sequential? (first values))
    (str "VALUES " (comma-join (for [x values]
                                 (str "(" (comma-join (map to-sql x)) ")"))))
    (str
      "(" (comma-join (map to-sql (keys (first values)))) ") VALUES "
      (comma-join (for [x values]
                    (str "(" (comma-join (map to-sql (vals x))) ")"))))))

(defmethod format-clause :query-values [[_ query-values] _]
  (to-sql query-values))

(defmethod format-clause :update [[_ table] _]
  (str "UPDATE " (to-sql table)))

(defmethod format-clause :set [[_ values] _]
  (str "SET " (comma-join (for [[k v] values]
                            (str (to-sql k) " = " (to-sql v))))))

(defmethod format-clause :delete-from [[_ table] _]
  (str "DELETE FROM " (to-sql table)))

(defmethod format-clause :over [[_ part-clause] _]
  (str "OVER " (to-sql part-clause)))

(defmethod format-clause :aggregate [[_ aggregate-fn] _]
  (str (to-sql aggregate-fn)))

(defmethod format-clause :partition-by [[_ fields] _]
  (str "PARTITION BY "
       (comma-join (map to-sql fields))))
