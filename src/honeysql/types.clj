(ns honeysql.types)

(defrecord SqlCall [name args])

(defn call
  "Represents a SQL function call. Name should be a keyword."
  [name & args]
  (SqlCall. name args))

;;;;

(defrecord SqlRaw [s])

(defn raw
  "Represents a raw SQL string"
  [s]
  (SqlRaw. (str s)))

;;;;

(defrecord SqlParam [name])

(defn param
  "Represents a SQL parameter which can be filled in later"
  [name]
  (SqlParam. name))

(defn param-name [^SqlParam param]
  (.name param))

;;;;

;; retain readers for backwards compatibility

(defn read-sql-call [form]
  (apply call form))

(defn read-sql-raw [form]
  (raw form))

(defn read-sql-param [form]
  (param form))
