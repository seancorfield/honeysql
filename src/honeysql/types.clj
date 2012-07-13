(ns honeysql.types)

(deftype SqlCall [name args _meta]
  Object
  (hashCode [this] (hash-combine (hash name) (hash args)))
  (equals [this x]
    (cond (identical? this x) true
          (instance? SqlCall x) (and (= (.name this) (.name x))
                                     (= (.args this) (.args x)))
          :else false))
  clojure.lang.IObj
  (meta [this] _meta)
  (withMeta [this m] (SqlCall. (.name this) (.args this) m)))

(defn call [name & args]
  (SqlCall. name args nil))

(defn read-sql-call [form]
  (apply call form))

(defmethod print-method SqlCall [^SqlCall o ^java.io.Writer w]
  (.write w (str "#sql/call " (pr-str (into [(.name o)] (.args o))))))

(defmethod print-dup SqlCall [o w]
  (print-method o w))

;;;;

(deftype SqlRaw [s _meta]
  Object
  (hashCode [this] (hash-combine (hash (class this)) (hash s)))
  (equals [this x] (and (instance? SqlRaw x) (= (.s this) (.s x))))
  clojure.lang.IObj
  (meta [this] _meta)
  (withMeta [this m] (SqlRaw. (.s this) m)))

(defn raw [s]
  (SqlRaw. (str s) nil))

(defn read-sql-raw [form]
  (raw form))

(defmethod print-method SqlRaw [^SqlRaw o ^java.io.Writer w]
  (.write w (str "#sql/raw " (pr-str (.s o)))))

(defmethod print-dup SqlRaw [o w]
  (print-method o w))
