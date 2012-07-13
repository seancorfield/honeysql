(ns honeysql.format
  (:require [clojure.string :as string]))

;;(set! *warn-on-reflection* true)

;;;;

(deftype SqlFn [name args])

(defn sql-fn [name & args]
  (SqlFn. name args))

(defn read-sql-fn [form]
  (apply sql-fn form))

(defmethod print-method SqlFn [^SqlFn o ^java.io.Writer w]
  (.write w (str "#sql/fn " (pr-str (into [(.name o)] (.args o))))))

(defmethod print-dup SqlFn [o w]
  (print-method o w))

;;;;

(deftype SqlRaw [s])

(defn sql-raw [s]
  (SqlRaw. (str s)))

(defn read-sql-raw [form]
  (sql-raw form))

(defmethod print-method SqlRaw [^SqlRaw o ^java.io.Writer w]
  (.write w (str "#sql/raw " (pr-str (.s o)))))

(defmethod print-dup SqlRaw [o w]
  (print-method o w))

;;;;

(defn comma-join [s]
  (string/join ", " s))

(defn space-join [s]
  (string/join " " s))

(defn paren-wrap [x]
  (str "(" x ")"))

(def ^:dynamic *params*
  "Will be bound to an atom-vector that accumulates SQL parameters across
  possibly-recursive function calls"
  nil)

(def ^:dynamic *fn-context?* false)

(def infix-fns
  #{"+" "-" "*" "/" "%" "mod" "|" "&" "^"
    "is" "=" ">" ">=" "<" "<=" "<>" "!="
    "and" "or" "xor"
    "in" "not in" "like" "regexp"})

(def fn-aliases
  {"not=" "!="
   "not-in" "not in"})

(def fn-handlers
  {"between" (fn [field upper lower]
               (str field " BETWEEN " upper " AND " lower))})

(def clause-order
  "Determines the order that clauses will be placed within generated SQL"
  [:select :from :join :where :group-by :having :order-by :limit :offset])

(declare to-sql)

(defn format-sql [sql-map]
  (binding [*params* (atom [])]
    (let [sql-str (to-sql sql-map)]
      (if (seq @*params*)
        [sql-str @*params*]
        [sql-str]))))

(defprotocol ToSql
  (-to-sql [x]))

(declare format-clause)

(extend-protocol ToSql
  clojure.lang.Keyword
  (-to-sql [x] (name x))
  clojure.lang.Symbol
  (-to-sql [x] (str x))
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
                      " AS "
                      (to-sql (second x)))))
  SqlFn
  (-to-sql [x] (binding [*fn-context?* true]
                 (let [fn-name (name (.name x))
                       fn-name (fn-aliases fn-name fn-name)
                       fn-name-upper (string/upper-case fn-name)
                       args (map to-sql (.args x))]
                   (if-let [handler (fn-handlers fn-name)]
                     (apply handler args)
                     (if (infix-fns fn-name)
                       (paren-wrap (string/join (str " " fn-name-upper " ") args))
                       (str fn-name-upper (paren-wrap (comma-join args))))))))
  SqlRaw
  (-to-sql [x] (.s x))
  clojure.lang.IPersistentMap
  (-to-sql [x] (let [clause-ops (filter #(contains? x %) clause-order)]
                 (paren-wrap
                  (space-join (map (comp format-clause #(find x %))
                                   clause-ops)))))
  nil
  (-to-sql [x] "NULL"))

(defn sqlable? [x]
  (satisfies? ToSql x))

(defn to-sql [x]
  (if (satisfies? ToSql x)
    (-to-sql x)
    (do
      (swap! *params* conj x)
      "?")))

;;;;

(defn format-predicate [pred]
  (if-not (sequential? pred)
    (to-sql pred)
    (let [[op & args] pred
          op-name (name op)]
      (if (= "not" op-name)
        (str "NOT " (format-predicate (first args)))
        (if (#{"and" "or" "xor"} op-name)
          (paren-wrap
           (string/join (str " " (string/upper-case op-name) " ")
                        (map format-predicate args)))
          (to-sql (apply sql-fn pred)))))))

(defmulti format-clause
  "Takes a map entry representing a clause and returns an SQL string"
  key)

(defmethod format-clause :select [[_ fields]]
  (str "SELECT " (comma-join (map to-sql fields))))

(defmethod format-clause :from [[_ tables]]
  (str "FROM " (comma-join (map to-sql tables))))

(defmethod format-clause :where [[_ pred]]
  (str "WHERE " (format-predicate pred)))

(defn format-join
  [table pred & [type]]
  (str (when type
         (str (string/upper-case (name type)) " "))
       "JOIN " (to-sql table)
       " ON " (format-predicate pred)))

(defmethod format-clause :join [[_ [table pred type]]]
  (format-join table pred type))

(defmethod format-clause :joins [[_ join-groups]]
  (space-join (map format-clause join-groups)))

(defmethod format-clause :group-by [[_ fields]]
  (str "GROUP BY " (comma-join (map to-sql fields))))

(defmethod format-clause :having [[_ pred]]
  (str "HAVING " (format-predicate pred)))

(defmethod format-clause :order-by [[_ fields]]
  (str "ORDER BY "
       (comma-join (for [field fields]
                     (if (sequential? field)
                       (let [[field order] field]
                         (str (to-sql field) " " (if (= :desc order)
                                                   "DESC" "ASC")))
                       (to-sql field))))))

(defmethod format-clause :limit [[_ limit]]
  (str "LIMIT " (to-sql limit)))

(defmethod format-clause :offset [[_ offset]]
  (str "OFFSET " (to-sql offset)))

