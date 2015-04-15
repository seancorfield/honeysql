(ns honeysql.types)

(deftype SqlCall [name args _meta]
  Object
  (hashCode [_] (hash-combine (hash name) (hash args)))
  (equals [this x]
    (cond (identical? this x) true
          (instance? SqlCall x) (let [^SqlCall x x]
                                  (and (= name (.name x))
                                       (= args (.args x))))
          :else false))
  clojure.lang.IObj
  (meta [_] _meta)
  (withMeta [_ m] (SqlCall. name args m)))

(defn call
  "Represents a SQL function call. Name should be a keyword."
  [name & args]
  (SqlCall. name args nil))

(defn read-sql-call [form]
  ;; late bind so that we get new class on REPL reset
  (apply (resolve `call) form))

(defmethod print-method SqlCall [^SqlCall o ^java.io.Writer w]
  (.write w (str "#sql/call " (pr-str (into [(.name o)] (.args o))))))

(defmethod print-dup SqlCall [o w]
  (print-method o w))

;;;;

(deftype SqlRaw [s _meta]
  Object
  (hashCode [this] (hash-combine (hash (class this)) (hash s)))
  (equals [_ x] (and (instance? SqlRaw x) (= s (.s ^SqlRaw x))))
  clojure.lang.IObj
  (meta [_] _meta)
  (withMeta [_ m] (SqlRaw. s m)))

(defn raw
  "Represents a raw SQL string"
  [s]
  (SqlRaw. (str s) nil))

(defn read-sql-raw [form]
  ;; late bind, as above
  ((resolve `raw) form))

(defmethod print-method SqlRaw [^SqlRaw o ^java.io.Writer w]
  (.write w (str "#sql/raw " (pr-str (.s o)))))

(defmethod print-dup SqlRaw [o w]
  (print-method o w))

;;;;

(deftype SqlParam [name _meta]
  Object
  (hashCode [this] (hash-combine (hash (class this)) (hash (name name))))
  (equals [_ x] (and (instance? SqlParam x) (= name (.name ^SqlParam x))))
  clojure.lang.IObj
  (meta [_] _meta)
  (withMeta [_ m] (SqlParam. name m)))

(defn param
  "Represents a SQL parameter which can be filled in later"
  [name]
  (SqlParam. name nil))

(defn param-name [^SqlParam param]
  (.name param))

(defn read-sql-param [form]
  ;; late bind, as above
  ((resolve `param) form))

(defmethod print-method SqlParam [^SqlParam o ^java.io.Writer w]
  (.write w (str "#sql/param " (pr-str (.name o)))))

(defmethod print-dup SqlParam [o w]
  (print-method o w))

;;;;

(deftype SqlArray [values _meta]
  Object
  (hashCode [this] (hash-combine (hash (class this)) (hash values)))
  (equals [_ x] (and (instance? SqlArray x) (= values (.values ^SqlArray x))))
  clojure.lang.IObj
  (meta [_] _meta)
  (withMeta [_ m] (SqlArray. values m)))

(defn array
  "Represents a SQL array."
  [values]
  (SqlArray. values nil))

(defn array-vals [^SqlArray a]
  (.values a))

(defn read-sql-array [form]
  ;; late bind, as above
  ((resolve `array) form))

(defmethod print-method SqlArray [^SqlArray a ^java.io.Writer w]
  (.write w (str "#sql/array " (pr-str (.values a)))))

(defmethod print-dup SqlArray [a w]
  (print-method a w))
