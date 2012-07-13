(ns honeysql.core
  (:refer-clojure :exclude [group-by format])
  (:require [honeysql.format :as format]
            [honeysql.types :as types]
            [honeysql.util :refer [defalias]]))

(defalias call types/call)
(defalias raw types/raw)
(defalias format format/format)

(defn select [& fields]
  (let [[m fields] (if (map? (first fields))
                     [(first fields) (rest fields)]
                     [{} fields])]
    (assoc m :select fields)))

(defn merge-select [sql-map & fields]
  (update-in sql-map [:select] concat fields))

(defn from [& tables]
  (let [[m tables] (if (map? (first tables))
                     [(first tables) (rest tables)]
                     [{} tables])]
    (assoc m :from tables)))

(defn merge-from [sql-map & tables]
  (update-in sql-map [:from] concat tables))

(defn where [& preds]
  (let [[m preds] (if (map? (first preds))
                    [(first preds) (rest preds)]
                    [{} preds])]
    (assoc m :where (if (= 1 (count preds))
                      (first preds)
                      (vec (cons :and preds))))))

(defn merge-where [sql-map & preds]
  (if (empty? preds)
    sql-map
    (let [[merge-op preds] (if (keyword? (first preds))
                             [(first preds) (rest preds)]
                             [:and preds])]
      (assoc sql-map :where (if (contains? sql-map :where)
                              (vec (concat [merge-op (:where sql-map)] preds))
                              (vec (if (= 1 (count preds))
                                     (first preds)
                                     (cons merge-op preds))))))))

(defn join [& clauses]
  (let [[m clauses] (if (map? (first clauses))
                      [(first clauses) (rest clauses)]
                      [{} clauses])]
    (assoc m :join clauses)))

(defn merge-join [sql-map & clauses]
  (update-in sql-map [:join] concat clauses))

(defn group-by [& fields]
  (let [[m fields] (if (map? (first fields))
                     [(first fields) (rest fields)]
                     [{} fields])]
    (assoc m :group-by fields)))

(defn merge-group-by [sql-map & fields]
  (update-in sql-map [:group-by] concat fields))

(defn having [& preds]
  (let [[m preds] (if (map? (first preds))
                    [(first preds) (rest preds)]
                    [{} preds])]
    (assoc m :having (if (= 1 (count preds))
                       (first preds)
                       (vec (cons :and preds))))))

(defn merge-having [sql-map & preds]
  (if (empty? preds)
    sql-map
    (let [[merge-op preds] (if (keyword? (first preds))
                             [(first preds) (rest preds)]
                             [:and preds])]
      (assoc sql-map :having (if (contains? sql-map :having)
                               (vec (concat [merge-op (:having sql-map)] preds))
                               (vec (if (= 1 (count preds))
                                      (first preds)
                                      (cons merge-op preds))))))))

(defn order-by [& fields]
  (let [[m fields] (if (map? (first fields))
                     [(first fields) (rest fields)]
                     [{} fields])]
    (assoc m :order-by fields)))

(defn merge-order-by [sql-map & fields]
  (update-in sql-map [:order-by] concat fields))

(defn limit
  ([l]
     (limit {} l))
  ([sql-map l]
     (assoc sql-map :limit l)))

(defn offset
  ([o]
     (offset {} o))
  ([sql-map o]
     (assoc sql-map :offset o)))

(defn modifiers [& ms]
  (let [[m ms] (if (map? (first ms))
                 [(first ms) (rest ms)]
                 [{} ms])]
    (assoc m :modifiers ms)))

(defn merge-modifiers [sql-map & ms]
  (update-in sql-map [:modifiers] concat ms))

(def ^:private handlers
  {:select select
   :from from
   :where where
   :join join
   :group-by group-by
   :having having
   :order-by order-by
   :limit limit
   :offset offset
   :modifiers modifiers})

(def ^:private merge-handlers
  {:select merge-select
   :from merge-from
   :where merge-where
   :join merge-join
   :group-by merge-group-by
   :having merge-having
   :order-by merge-order-by
   :limit limit
   :offset offset
   :modifiers merge-modifiers})

(defn- build-sql [handlers clauses]
  (let [[base clauses] (if (map? (first clauses))
                         [(first clauses) (rest clauses)]
                         [{} clauses])]
    (reduce
     (fn [sql-map [op args]]
       (let [handler (handlers op)]
         (if (or (#{:where :having} op) (not (coll? args)))
           (handler sql-map args)
           (apply handler sql-map args))))
     base
     (partition 2 clauses))))

(defn sql [& clauses]
  (build-sql handlers clauses))

(defn merge-sql [& clauses]
  (build-sql merge-handlers clauses))
