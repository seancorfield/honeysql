(ns honeysql.core
  (:refer-clojure :exclude [group-by format])
  (:require [honeysql.format :as format]
            [honeysql.types :as types]
            [honeysql.helpers :refer [build-clause]]
            [honeysql.util :refer [defalias]]))

(defalias call types/call)
(defalias raw types/raw)
(defalias format format/format)
(defalias format-predicate format/format-predicate)

(defn build [& clauses]
  (let [[base clauses] (if (map? (first clauses))
                         [(first clauses) (rest clauses)]
                         [{} clauses])]
    (reduce
     (fn [sql-map [op args]]
       (build-clause op sql-map args))
     (if (empty? base)
       base
       (apply build (apply concat base)))
     (partition 2 clauses))))