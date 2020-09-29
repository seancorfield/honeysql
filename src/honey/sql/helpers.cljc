;; copyright (c) 2020 sean corfield, all rights reserved

(ns honey.sql.helpers
  "Helper functions for the built-in clauses in honey.sql."
  (:refer-clojure :exclude [update set group-by for])
  (:require [honey.sql :as h]))

(defn- default-merge [current args]
  (into (vec current) args))

(def ^:private special-merges
  {:where (fn [current args]
            (if (= :and (first (first args)))
              (default-merge current args)
              (-> [:and]
                  (into current)
                  (into args))))})

(defn- helper-merge [data k args]
  (let [merge-fn (special-merges k default-merge)]
    (clojure.core/update data k merge-fn args)))

(defn- generic [k args]
  (if (map? (first args))
    (let [[data & args] args]
      (helper-merge data k args))
    (helper-merge {} k args)))

(defn with [& args] (generic :with args))
(defn with-recursive [& args] (generic :with-recursive args))
(defn intersect [& args] (generic :intersect args))
(defn union [& args] (generic :union args))
(defn union-all [& args] (generic :union-all args))
(defn except [& args] (generic :except args))
(defn except-all [& args] (generic :except-all args))
(defn select [& args] (generic :select args))
(defn insert-into [& args] (generic :insert-into args))
(defn update [& args] (generic :update args))
(defn delete [& args] (generic :delete args))
(defn delete-from [& args] (generic :delete-from args))
(defn truncate [& args] (generic :truncate args))
(defn columns [& args] (generic :columns args))
(defn composite [& args] (generic :composite args))
(defn set [& args] (generic :set args))
(defn from [& args] (generic :from args))
(defn join [& args] (generic :join args))
(defn left-join [& args] (generic :left-join args))
(defn right-join [& args] (generic :right-join args))
(defn inner-join [& args] (generic :inner-join args))
(defn outer-join [& args] (generic :outer-join args))
(defn full-join [& args] (generic :full-join args))
(defn cross-join [& args] (generic :cross-join args))
(defn where [& args] (generic :where args))
(defn group-by [& args] (generic :group-by args))
(defn having [& args] (generic :having args))
(defn order-by [& args] (generic :order-by args))
(defn limit [& args] (generic :limit args))
(defn offset [& args] (generic :offset args))
(defn for [& args] (generic :for args))
(defn values [& args] (generic :values args))
(defn on-conflict [& args] (generic :on-conflict args))
(defn on-constraint [& args] (generic :on-constraint args))
(defn do-nothing [& args] (generic :do-nothing args))
(defn do-update-set [& args] (generic :do-update-set args))
(defn returning [& args] (generic :returning args))

#?(:clj
    (assert (= (clojure.core/set @@#'h/base-clause-order)
               (clojure.core/set (map keyword (keys (ns-publics *ns*)))))))
