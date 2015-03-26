(ns honeysql.format
  (:refer-clojure :exclude [format])
  (:require [honeysql.types :refer [call raw param param-name]]
            [clojure.string :as string])
  (:import [honeysql.types SqlCall SqlRaw SqlParam]))

;;(set! *warn-on-reflection* true)

;;;;

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

(def ^:dynamic *subquery?* false)

(def ^:dynamic *builder* nil)

(def ^:private quote-fns
  {:ansi #(str \" % \")
   :mysql #(str \` % \`)
   :sqlserver #(str \[ % \])
   :oracle #(str \" % \")})

(def ^:private parameterizers
  {:postgresql #(str "$" (swap! *all-param-counter* inc))
   :jdbc (constantly "?")})

(def ^:dynamic *quote-identifier-fn* nil)
(def ^:dynamic *parameterizer* nil)

(defn- undasherize [s]
  (string/replace s "-" "_"))

(defn append-str [^String s]
  (.append ^StringBuilder *builder* s))

(defn append-obj [o]
  (.append ^StringBuilder *builder* o))

(defn append-char [c]
  (.append ^StringBuilder *builder* (char c)))

(defn do-join [sep appender xs]
  (when (seq xs)
    (appender (first xs))
    (doseq [x (rest xs)]
      (append-str sep)
      (appender x))))

(defn close-paren []
  (.append ^StringBuilder *builder* \)))

(defn do-paren-join [sep appender xs]
  (append-char \()
  (do-join sep appender xs)
  (close-paren))

(defn quote-identifier [x & {:keys [style split] :or {split true}}]
  (let [qf (if style
             (quote-fns style)
             *quote-identifier-fn*)
        s (cond
            (or (keyword? x) (symbol? x)) (undasherize (name x))
            (string? x) (if qf x (undasherize x))
            :else (str x))]
    (if-not qf
      (append-str s)
      (let [qf* #(if (= "*" %) % (qf %))]
        (if-not split
         (append-str (qf* s))
         (do-join
           "."
           append-str
           (map qf* (string/split s #"\."))))))))

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
  (do-paren-join
    " AND "
    (fn [[a b]] (fn-handler op a b))
    (partition 2 1 args)))

(defmethod fn-handler :default [op & args]
  (if (infix-fns op)
    (do-paren-join
      (str " " op " ")
      to-sql
      args)
    (do
      (append-str op)
      (do-paren-join ", " to-sql args))))

(defmethod fn-handler "count-distinct" [_ & args]
  (append-str "COUNT(DISTINCT")
  (do-join ", " to-sql args)
  (close-paren)
  )

(defmethod fn-handler "distinct-on" [_ & args]
  (append-str "DISTINCT ON (")
  (do-join ", " to-sql args)
  (close-paren)
  )

(defmethod fn-handler "cast" [_ field cast-to-type]
  (append-str "CAST(")
  (to-sql field)
  (append-str " AS ")
  (to-sql cast-to-type)
  (close-paren))

(defmethod fn-handler "=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "=" a b more)
    (cond
     (nil? a) (do (to-sql b) (append-str " IS NULL"))
     (nil? b) (do (to-sql a) (append-str " IS NULL"))
     :else (do (to-sql a) (append-str " = ") (to-sql b)))))

(defmethod fn-handler "<>" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<>" a b more)
    (cond
     (nil? a) (do (to-sql b) (append-str " IS NOT NULL"))
     (nil? b) (do (to-sql a) (append-str " IS NOT NULL"))
     :else (do (to-sql a) (append-str " <> ") (to-sql b)))))

(defmethod fn-handler "<" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<" a b more)
    (do (to-sql a) (append-str " < ") (to-sql b))))

(defmethod fn-handler "<=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops "<=" a b more)
    (do (to-sql a) (append-str " <= ") (to-sql b))))

(defmethod fn-handler ">" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">" a b more)
    (do (to-sql a) (append-str " > ") (to-sql b))))

(defmethod fn-handler ">=" [_ a b & more]
  (if (seq more)
    (apply expand-binary-ops ">=" a b more)
    (do (to-sql a) (append-str " >= ") (to-sql b))))

(defmethod fn-handler "between" [_ field lower upper]
  (to-sql field)
  (append-str " BETWEEN ")
  (to-sql lower)
  (append-str " AND ")
  (to-sql upper))

;; Handles MySql's MATCH (field) AGAINST (pattern). The third argument
;; can be a set containing one or more of :boolean, :natural, or :expand.
(defmethod fn-handler "match" [_ fields pattern & [opts]]
  (append-str "MATCH (")
  (do-join ", " to-sql (if (coll? fields) fields [fields]))
  (append-str ") AGAINST (")
  (to-sql pattern)
  (doseq [opt opts]
    (append-str
      (case opt
        :boolean " IN BOOLEAN MODE"
        :natural " IN NATURAL LANGUAGE MODE"
        :expand " WITH QUERY EXPANSION")))
  (close-paren))

(def default-clause-priorities
  "Determines the order that clauses will be placed within generated SQL"
  {:with 30
   :with-recursive 40
   :select 50
   :insert-into 60
   :update 70
   :delete-from 80
   :columns 90
   :set 100
   :from 110
   :join 120
   :left-join 130
   :right-join 140
   :full-join 150
   :where 160
   :group-by 170
   :having 180
   :order-by 190
   :limit 200
   :offset 210
   :values 220
   :query-values 230})

(def clause-store (atom default-clause-priorities))

(defn register-clause! [clause-key priority]
  (swap! clause-store assoc clause-key priority))

(defn sort-clauses [clauses]
  (let [m @clause-store]
    (sort-by #(m % Long/MAX_VALUE) clauses)))

(defn format
  "Takes a SQL map and optional input parameters and returns a vector
  of a SQL string and parameters, as expected by clojure.java.jdbc.

  Input parameters will be filled into designated spots according to
  name (if a map is provided) or by position (if a sequence is provided).

  Instead of passing parameters, you can use keyword arguments:
    :params - input parameters
    :quoting - quote style to use for identifiers; one of :ansi (PostgreSQL),
               :mysql, :sqlserver, or :oracle. Defaults to no quoting.
    :parameterizer - style of parameter naming, one of :postgresql or :jdbc, defaults to :jdbc
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
              *parameterizer* (parameterizers (or (:parameterizer opts) :jdbc))
              *builder* (StringBuilder.)]
      (to-sql sql-map)
      (let [sql-str (str *builder*)]
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
                                      *quote-identifier-fn*)
            *builder* (StringBuilder.)]
    (format-predicate* pred)
    (let [sql-str (str *builder*)]
      (if (seq @*params*)
        (into [sql-str] @*params*)
        [sql-str]))))

(defprotocol ToSql
  (-to-sql [x]))

(declare -format-clause)

(extend-protocol ToSql
  clojure.lang.Keyword
  (-to-sql [x]
    (let [s (name x)]
      (case (.charAt s 0)
        \% (let [call-args (string/split (subs s 1) #"\." 2)]
             (to-sql (apply call (map keyword call-args))))
        \? (to-sql (param (keyword (subs s 1))))
        (quote-identifier x))))
  clojure.lang.Symbol
  (-to-sql [x] (quote-identifier x))
  java.lang.Number
  (-to-sql [x] (append-obj x))
  java.lang.Boolean
  (-to-sql [x]
    (append-str (if x "TRUE" "FALSE")))
  clojure.lang.Sequential
  (-to-sql [x]
    (if *fn-context?*
      ;; list argument in fn call
      (do-paren-join ", " to-sql x)
      ;; alias
      (do
        (to-sql (first x))
        ; Omit AS in FROM, JOIN, etc. - Oracle doesn't allow it
        (append-str
          (if (= :select *clause*)
            " AS "
            " "))
        (if (string? (second x))
          (quote-identifier (second x))
          (to-sql (second x))))))
  SqlCall
  (-to-sql [x]
    (binding [*fn-context?* true]
       (let [fn-name (name (.name x))
             fn-name (fn-aliases fn-name fn-name)]
         (apply fn-handler fn-name (.args x)))))
  SqlRaw
  (-to-sql [x] (append-str (.s x)))
  clojure.lang.IPersistentMap
  (-to-sql [x]
    (when *subquery?*
      (append-char \())
    (binding [*subquery?* true
              *fn-context?* false]
      (do-join
        " "
        (comp #(-format-clause % x) #(find x %))
        (sort-clauses (keys x))))
    (when *subquery?*
      (close-paren)))
  nil
  (-to-sql [x] (append-str "NULL"))
  Object
  (-to-sql [x]
    (let [[x pname] (if (instance? SqlParam x)
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
      (append-obj (*parameterizer*)))))

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
        (do (append-str "NOT ") (format-predicate* (first args)))
        (if (#{"and" "or" "xor"} op-name)
          (do-paren-join
            (str " " (string/upper-case op-name) " ")
            format-predicate*
            args)
          (to-sql (apply call pred)))))))

(defmulti format-clause
  "Takes a map entry representing a clause and returns an SQL string"
  (fn [clause _] (key clause)))

(defn- -format-clause
  [clause _]
  (binding [*clause* (key clause)]
    (format-clause clause _)))

(defmethod format-clause :default [& _])

(defmethod format-clause :select [[_ fields] sql-map]
  (append-str "SELECT ")
  (doseq [m (:modifiers sql-map)]
    (append-str (string/upper-case (name m)))
    (append-char \space))
  (do-join ", " to-sql fields))

(defmethod format-clause :from [[_ tables] _]
  (append-str "FROM ")
  (do-join ", " to-sql tables))

(defmethod format-clause :where [[_ pred] _]
  (append-str "WHERE ")
  (format-predicate* pred))

(defn format-join [type table pred]
  (when type
    (append-str (string/upper-case (name type)))
    (append-char \space))
  (append-str "JOIN ")
  (to-sql table)
  (append-str " ON ")
  (format-predicate* pred))

(defmethod format-clause :join [[_ join-groups] _]
  (do-join
    " "
    #(apply format-join :inner %)
    (partition 2 join-groups)))

(defmethod format-clause :left-join [[_ join-groups] _]
  (do-join
    " "
    #(apply format-join :left %)
    (partition 2 join-groups)))

(defmethod format-clause :right-join [[_ join-groups] _]
  (do-join
    " "
    #(apply format-join :right %)
    (partition 2 join-groups)))

(defmethod format-clause :full-join [[_ join-groups] _]
  (do-join
    " "
    #(apply format-join :full %)
    (partition 2 join-groups)))

(defmethod format-clause :group-by [[_ fields] _]
  (append-str "GROUP BY ")
  (do-join ", " to-sql fields))

(defmethod format-clause :having [[_ pred] _]
  (append-str "HAVING ")
  (format-predicate* pred))

(defmethod format-clause :order-by [[_ fields] _]
  (append-str "ORDER BY ")
  (do-join
    ", "
    (fn [field]
      (if (sequential? field)
        (let [[field order] field]
          (to-sql field)
          (append-str
            (if (= :desc order) " DESC" " ASC")))
        (to-sql field)))
    fields))

(defmethod format-clause :limit [[_ limit] _]
  (append-str "LIMIT ")
  (to-sql limit))

(defmethod format-clause :offset [[_ offset] _]
  (append-str "OFFSET ")
  (to-sql offset))

(defmethod format-clause :insert-into [[_ table] _]
  (append-str "INSERT INTO ")
  (if (and (sequential? table) (sequential? (first table)))
    (do
      (to-sql (ffirst table))
      (append-char \space)
      (do-paren-join
        ", "
        to-sql
        (second (first table)))
      (append-char \space)
      (to-sql (second table)))
    (to-sql table)))

(defmethod format-clause :columns [[_ fields] _]
  (do-paren-join ", " to-sql fields))

(defmethod format-clause :values [[_ values] _]
  (if (sequential? (first values))
    (do
      (append-str "VALUES ")
      (do-join
        ", "
        #(do-paren-join to-sql %)
        values))
    (do
      (do-paren-join ", " to-sql (keys (first values)))
      (append-str " VALUES ")
      (do-join
        ", "
        #(do-paren-join ", " to-sql (vals %))
        values))))

(defmethod format-clause :query-values [[_ query-values] _]
  (to-sql query-values))

(defmethod format-clause :update [[_ table] _]
  (append-str "UPDATE ")
  (to-sql table))

(defmethod format-clause :set [[_ values] _]
  (append-str "SET ")
  (do-join
    ", "
    (fn [[k v]]
      (to-sql k)
      (append-str " = ")
      (to-sql v))
    values))

(defmethod format-clause :delete-from [[_ table] _]
  (append-str "DELETE FROM ")
  (to-sql table))
  
(defn cte->sql
  [[cte-name query]]
  (to-sql cte-name)
  (append-str " AS ")
  (to-sql query))

(defmethod format-clause :with [[_ ctes] _]
  (append-str "WITH ")
  (do-join ", " cte->sql ctes))

(defmethod format-clause :with-recursive [[_ ctes] _]
  (append-str "WITH RECURSIVE ")
  (do-join ", " cte->sql ctes))

(defmethod format-clause :union [[_ maps] _]
  (do-join " UNION " to-sql maps))

(defmethod format-clause :union-all [[_ maps] _]
  (do-join " UNION ALL " to-sql maps))
