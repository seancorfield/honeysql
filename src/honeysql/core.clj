(ns honeysql.core
  (:refer-clojure :exclude [group-by format])
  (:require [honeysql.format :as format]
            [honeysql.types :as types]
            [honeysql.helpers :refer [build-clause]]
            [honeysql.util :refer [defalias]]
            [clojure.string :as string]))

(defalias call types/call)
(defalias raw types/raw)
(defalias param types/param)
(defalias format format/format)
(defalias format-predicate format/format-predicate)
(defalias quote-identifier format/quote-identifier)

(defn qualify
  "Takes one or more keyword or string qualifers and name. Returns
  a keyword of the concatenated qualifiers and name separated by periods.

  (qualify :foo \"bar\" :baz) => :foo.bar.baz"
  [& qualifiers+name]
  (keyword
   (string/join "." (map #(if (keyword? %) (name %) (str %))
                         (remove nil? qualifiers+name)))))

(defn build
  "Takes a series of clause+data pairs and returns a SQL map. Example:

      (build :select [:a :b]
             :from :bar)

  Clauses are defined with the honeysql.helpers/build-clause multimethod.
  Built-in clauses include:

      :select, :merge-select, :un-select
      :from, :merge-from
      :join, :merge-join
      :left-join, :merge-left-join
      :right-join, :merge-right-join
      :where, :merge-where
      :group-by, :merge-group-by
      :having, :merge-having
      :limit
      :offset
      :modifiers, :merge-modifiers
      :insert-into
      :columns, :merge-columns
      :values, :merge-values
      :query-values
      :update
      :set
      :delete-from
      :returning"
  [& clauses]
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
