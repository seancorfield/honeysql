(ns honeysql.util)

(defmacro defalias [sym var-sym]
  `(let [v# (var ~var-sym)]
     (intern *ns* (with-meta (quote ~sym) (meta v#)) @v#)))