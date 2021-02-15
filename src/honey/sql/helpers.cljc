;; copyright (c) 2020-2021 sean corfield, all rights reserved

(ns honey.sql.helpers
  "Helper functions for the built-in clauses in honey.sql."
  (:refer-clojure :exclude [update set group-by for partition-by])
  (:require [honey.sql]))

;; implementation helpers:

(defn- default-merge [current args]
  (into (vec current) args))

(defn- and-merge
  [current args]
  (let [args (remove nil? args)]
    (cond (= :and (first current))
          (default-merge current args)
          (seq current)
          (if (seq args)
            (default-merge [:and current] args)
            current)
          (= 1 (count args))
          (vec (first args))
          (seq args)
          (default-merge [:and] args)
          :else
          (vec current))))

(def ^:private special-merges
  {:where  #'and-merge
   :having #'and-merge})

(defn- helper-merge [data k args]
  (let [merge-fn (special-merges k default-merge)]
    (clojure.core/update data k merge-fn args)))

(defn- generic [k args]
  (if (map? (first args))
    (let [[data & args] args]
      (helper-merge data k args))
    (helper-merge {} k args)))

(defn- generic-1 [k [data arg]]
  (if arg
    (assoc data k arg)
    (assoc {} k data)))

;; for every clause, there is a public helper

(defn alter-table
  "Alter table takes a SQL entity (the name of the
  table to modify) and any number of optional SQL
  clauses to be applied in a single statement.

  (alter-table :foo (add-column :id :int nil))

  If only the SQL entity is provided, the result
  needs to be combined with another SQL clause to
  modify the table.

  (-> (alter-table :foo) (add-column :id :int nil))"
  {:arglists '([table & clauses])}
  [& args]
  (generic :alter-table args))

(defn add-column
  "Add a single column to a table (see `alter-table`).

  Accepts any number of SQL elements that describe
  a column:

  (add-column :name [:varchar 32] [:not nil])"
  [& col-elems]
  (generic :add-column col-elems))

(defn drop-column
  "Takes a single column name (use with `alter-table`).

  (alter-table :foo (drop-column :bar))"
  {:arglists '([col])}
  [& args]
  (generic-1 :drop-column args))

(defn modify-column
  "Like add-column, accepts any number of SQL elements
  that describe the new column definition:

  (modify-column :name [:varchar 64] [:not nil])"
  [& col-elems]
  (generic :modify-column col-elems))

(defn rename-column
  "Accepts two column names: the original name and the
  new name to which it should be renamed:

  (rename-column :name :full-name)"
  {:arglists '([old-col new-col])}
  [& args]
  (generic :rename-column args))

(defn add-index
  "Like add-column, this accepts any number of SQL
  elements that describe a new index to be added:

  (add-index :unique :name-key :first-name :last-name)

  Produces: UNIQUE name_key(first_name, last_name)"
  {:arglist '([& index-elems])}
  [& args]
  (generic :add-index args))

(defn drop-index
  "Like drop-table, accepts a single index name:

  (drop-index :name-key)"
  [& args]
  (generic-1 :drop-index args))

(defn rename-table
  "Accepts a single table name and, despite its name,
  actually means RENAME TO:

  (alter-table :foo (rename-table :bar))

  Produces: ALTER TABLE foo RENAME TO bar"
  {:arglist '([new-table])}
  [& args]
  (generic-1 :rename-table args))

(defn create-table
  "Accepts a table name to create and optionally a
  flag to trigger IF NOT EXISTS in the SQL:

  (create-table :foo)
  (create-table :foo :if-not-exists)

  That second argument can be truthy value but using
  that keyword is recommended for clarity."
  {:arglists '([table] [table if-not-exists])}
  [& args]
  (generic :create-table args))

(defn create-extension
  "Accepts an extension name to create and optionally a
  flag to trigger IF NOT EXISTS in the SQL:

  (create-extension :postgis)
  (create-extension :postgis :if-not-exists)

  That second argument can be truthy value but using
  that keyword is recommended for clarity."
  {:arglists '([extension] [extension if-not-exists])}
  [& args]
  (generic :create-extension args))

(defn with-columns
  "Accepts any number of column descriptions. Each
  column description is a sequence of SQL elements
  that specify the name and the attributes.

  Can also accept a single argument which is a
  collection of column descriptions (mostly for
  compatibility with nilenso/honeysql-postgres
  which used to be needed for DDL)."
  {:arglists '([& col-specs] [col-spec-coll])}
  [& args]
  ;; special case so (with-columns [[:col-1 :definition] [:col-2 :definition]])
  ;; also works in addition to (with-columns [:col-1 :definition] [:col-2 :definition])
  (cond (and (= 1 (count args)) (sequential? (first args)) (sequential? (ffirst args)))
        (generic-1 :with-columns args)
        (and (= 2 (count args)) (sequential? (second args)) (sequential? (fnext args)))
        (generic-1 :with-columns args)
        :else
        (generic :with-columns args)))

(defn create-view
  "Accepts a single view name to create.

  (-> (create-view :cities)
      (select :*) (from :city))"
  [& args]
  (generic-1 :create-view args))

(defn drop-table
  "Accepts one or more table names to drop.

  (drop-table :foo)"
  [& tables]
  (generic :drop-table tables))

(defn drop-extension
  "Accepts one or more extension names to drop."
  [& extensions]
  (generic :drop-extension extensions))

(defn nest
  [& args]
  (generic :nest args))

(defn with
  [& args]
  (generic :with args))

(defn with-recursive
  [& args]
  (generic :with-recursive args))

;; these five need to supply an empty hash map since they wrap
;; all of their arguments:
(defn intersect
  [& args]
  (generic :intersect (cons {} args)))

(defn union
  [& args]
  (generic :union (cons {} args)))

(defn union-all
  [& args]
  (generic :union-all (cons {} args)))

(defn except
  [& args]
  (generic :except (cons {} args)))

(defn except-all
  [& args]
  (generic :except-all (cons {} args)))

(defn select
  [& args]
  (generic :select args))

(defn select-distinct
  [& args]
  (generic :select-distinct args))

(defn select-distinct-on
  [& args]
  (generic :select-distinct-on args))

(defn insert-into
  [& args]
  (generic :insert-into args))

(defn update
  [& args]
  (generic :update args))

(defn delete
  [& args]
  (generic-1 :delete args))

(defn delete-from
  [& args]
  (generic :delete-from args))

(defn truncate
  [& args]
  (generic :truncate args))

(defn columns
  [& args]
  (generic :columns args))

(defn set
  [& args]
  (generic-1 :set args))

(defn from
  [& args]
  (generic :from args))

(defn using
  [& args]
  (generic :using args))

(defn join
  [& args]
  (generic :join args))

(defn left-join
  [& args]
  (generic :left-join args))

(defn right-join
  [& args]
  (generic :right-join args))

(defn inner-join
  [& args]
  (generic :inner-join args))

(defn outer-join
  [& args]
  (generic :outer-join args))

(defn full-join
  [& args]
  (generic :full-join args))

(defn cross-join
  [& args]
  (generic :cross-join args))

(defn where
  [& args]
  (generic :where args))

(defn group-by
  [& args]
  (generic :group-by args))

(defn having
  [& args]
  (generic :having args))

(defn window
  [& args]
  (generic :window args))

(defn partition-by
  [& args]
  (generic :partition-by args))

(defn order-by
  [& args]
  (generic :order-by args))

(defn limit
  [& args]
  (generic-1 :limit args))

(defn offset
  [& args]
  (generic-1 :offset args))

(defn for
  [& args]
  (generic-1 :for args))

(defn values
  [& args]
  (generic-1 :values args))

(defn on-conflict
  [& args]
  (generic-1 :on-conflict args))

(defn on-constraint
  [& args]
  (generic :on-constraint args))

(defn do-nothing
  [& args]
  (generic :do-nothing args))

(defn do-update-set
  [& args]
  (generic :do-update-set args))

(defn returning
  [& args]
  (generic :returning args))

;; helpers that produce non-clause expressions -- must be listed below:
(defn composite
  [& args]
  (into [:composite] args))

;; to make this easy to use in a select, wrap it so it becomes a function:
(defn over
  [& args]
  [(into [:over] args)])

;; this helper is intended to ease the migration from nilenso:
(defn upsert
  ([clause] (upsert {} clause))
  ([data clause]
   (let [{:keys [on-conflict do-nothing do-update-set where]} clause]
     (cond-> data
       on-conflict
       (assoc :on-conflict on-conflict)
       do-nothing
       (assoc :do-nothing do-nothing)
       do-update-set
       (assoc :do-update-set (if where
                               {:fields do-update-set
                                :where  where}
                               do-update-set))))))

#?(:clj
    (assert (= (clojure.core/set (conj @@#'honey.sql/base-clause-order
                                       :composite :over :upsert))
               (clojure.core/set (map keyword (keys (ns-publics *ns*)))))))
