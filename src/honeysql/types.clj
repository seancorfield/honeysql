(ns honeysql.types)

(defrecord SqlCall [name args])

(defn call
  "Represents a SQL function call. Name should be a keyword."
  [name & args]
  (SqlCall. name args))

(defn read-sql-call [form]
  ;; late bind so that we get new class on REPL reset
  (apply (resolve `call) form))

(defmethod print-method SqlCall [^SqlCall o ^java.io.Writer w]
  (.write w (str "#sql/call " (pr-str (into [(.-name o)] (.-args o))))))

(defmethod print-dup SqlCall [o w]
  (print-method o w))

;;;;

(defrecord SqlRaw [s])

(defn raw
  "Represents a raw SQL string"
  [s]
  (SqlRaw. (str s)))

(defn read-sql-raw [form]
  ;; late bind, as above
  ((resolve `raw) form))

(defmethod print-method SqlRaw [^SqlRaw o ^java.io.Writer w]
  (.write w (str "#sql/raw " (pr-str (.-s o)))))

(defmethod print-dup SqlRaw [o w]
  (print-method o w))

;;;;

(defrecord SqlParam [name])

(defn param
  "Represents a SQL parameter which can be filled in later"
  [name]
  (SqlParam. name))

(defn param-name [^SqlParam param]
  (.-name param))

(defn read-sql-param [form]
  ;; late bind, as above
  ((resolve `param) form))

(defmethod print-method SqlParam [^SqlParam o ^java.io.Writer w]
  (.write w (str "#sql/param " (pr-str (.-name o)))))

(defmethod print-dup SqlParam [o w]
  (print-method o w))

;;;;

(defrecord SqlArray [values])

(defn array
  "Represents a SQL array."
  [values]
  (SqlArray. values))

(defn array-vals [^SqlArray a]
  (.-values a))

(defn read-sql-array [form]
  ;; late bind, as above
  ((resolve `array) form))

(defmethod print-method SqlArray [^SqlArray a ^java.io.Writer w]
  (.write w (str "#sql/array " (pr-str (.-values a)))))

(defmethod print-dup SqlArray [a w]
  (print-method a w))
