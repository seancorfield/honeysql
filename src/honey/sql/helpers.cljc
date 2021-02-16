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

  (with-columns [:id :int [:not nil]]
                [:name [:varchar 32] [:default \"\"]])

  Produces:
    id INT NOT NULL,
    name VARCHAR(32) DEFAULT ''

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
  "Accepts any number of SQL clauses (queries) on
  which to perform a set intersection."
  [& clauses]
  (generic :intersect (cons {} clauses)))

(defn union
  "Accepts any number of SQL clauses (queries) on
  which to perform a set union."
  [& clauses]
  (generic :union (cons {} clauses)))

(defn union-all
  "Accepts any number of SQL clauses (queries) on
  which to perform a set union all."
  [& clauses]
  (generic :union-all (cons {} clauses)))

(defn except
  "Accepts any number of SQL clauses (queries) on
  which to perform a set except."
  [& clauses]
  (generic :except (cons {} clauses)))

(defn except-all
  "Accepts any number of SQL clauses (queries) on
  which to perform a set except all."
  [& clauses]
  (generic :except-all (cons {} clauses)))

(defn select
  "Accepts any number of column names, or column/alias
  pairs, or SQL expressions (optionally aliased):

  (select :id [:foo :bar] [[:max :quux]])

  Produces: SELECT id, foo AS bar, MAX(quux)

  The special column name :* produces * for 'all columns'.
  You can also specify :t.* for 'all columns' from the
  table (or alias) t."
  [& exprs]
  (generic :select exprs))

(defn select-distinct
  "Like `select` but produces SELECT DISTINCT."
  [& args]
  (generic :select-distinct args))

(defn select-distinct-on
  "Accepts a sequence of one or more columns for the
  distinct clause, followed by any number of column
  names, or column/alias pairs, or SQL expressions
  (optionally aliased), as for `select`:

  (select-distinct-on [:a :b] :c [:d :dd])

  Produces: SELECT DISTINCT ON(a, b) c, d AS dd"
  {:arglists '([distinct-cols & exprs])}
  [& args]
  (generic :select-distinct-on args))

(defn insert-into
  "Accepts a table name or a table/alias pair. That
  can optionally be followed by a collection of
  column names. That can optionally be followed by
  a (select) statement clause.

  (insert-into :table)
  (insert-into [:table :t])
  (insert-into :table [:id :name :cost])
  (insert-into :table (-> (select :*) (from :other)))
  (insert-into [:table :t]
               [:id :name :cost]
               (-> (select :*) (from :other)))"
  {:arglists '([table] [table cols] [table statement] [table cols statement])}
  [& args]
  (let [[table cols statement] args]
    (if (and (sequential? cols) (map? statement))
      (generic :insert-into [[table cols] statement])
      (generic :insert-into args))))

(defn update
  "Accepts either a table name or a table/alias pair.

  (-> (update :table) (set {:id 1 :cost 32.1}))"
  {:arglists '([table])}
  [& args]
  (generic-1 :update args))

(defn delete
  "For deleting from multiple tables.
  Accepts a collection of table names to delete from.

  (-> (delete [:films :directors]) (where [:= :id 1]))"
  {:arglists '([table-coll])}
  [& args]
  (generic-1 :delete args))

(defn delete-from
  "For deleting from a single table.
  Accepts a single table name to delete from.

  (-> (delete-from :films) (where [:= :id 1]))"
  {:arglists '([table])}
  [& args]
  (generic :delete-from args))

(defn truncate
  "Accepts a single table name to truncate."
  {:arglists '([table])}
  [& args]
  (generic-1 :truncate args))

(defn columns
  "To be used with `insert-into` to specify the list of
  column names for the insert operation. Accepts any number
  of column names:

  (-> (insert-into :foo)
      (columns :a :b :c)
      (values [[1 2 3] [2 4 6]]))

  Produces:
    INSERT INTO foo (a, b, c) VALUES (?, ?, ?), (?, ?, ?)
  Parameters: 1 2 3 2 4 6"
  [& cols]
  (generic :columns cols))

(defn set
  "Accepts a hash map specifying column names and the
  values to be assigned to them, as part of `update`:

  (-> (update :foo)
      (set {:a 1 :b nil}))

  Produces: UPDATE foo SET a = ?, b = NULL"
  {:arglists '([col-set-map])}
  [& args]
  (generic-1 :set args))

(defn from
  "Accepts one or more table names, or table/alias pairs.

  (-> (select :*)
      (from [:foo :bar]))

  Produces: SELECT * FROM foo AS bar"
  [& tables]
  (generic :from tables))

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
  "Accepts one or more SQL expressions (conditions) and
  combines them with AND:

  (where [:= :status 0] [:<> :task \"backup\"])

  Produces: WHERE (status = ?) AND (task <> ?)
  Parameters: 0 \"backup\""
  [& exprs]
  (generic :where exprs))

(defn group-by
  [& args]
  (generic :group-by args))

(defn having
  "Like `where`, accepts one or more SQL expressions
  (conditions) and combines them with AND:

  (having [:> :count 0] [:<> :name nil])

  Produces: HAVING (count > ?) AND (name IS NOT NULL)
  Parameters: 0"
  [& exprs]
  (generic :having exprs))

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
  "Specific to MySQL, accepts a single SQL expression:

  (limit 40)

  Produces: LIMIT ?
  Parameters: 40"
  {:arglists '([limit])}
  [& args]
  (generic-1 :limit args))

(defn offset
  "Specific to MySQL, accepts a single SQL expression:

  (offset 10)

  Produces: OFFSET ?
  Parameters: 10"
  {:arglists '([offset])}
  [& args]
  (generic-1 :offset args))

(defn for
  [& args]
  (generic-1 :for args))

(defn values
  "Accepts a single argument: a collection of row values.
  Each row value can be either a sequence of column values
  or a hash map of column name/column value pairs.

  Used with `insert-into`.

  (-> (insert-into :foo)
      (values [{:id 1, :name \"John\"}
               {:id 2, :name \"Fred\"}]))

  Produces: INSERT INTO foo (id, name) VALUES (?, ?), (?, ?)
  Parameters: 1 \"John\" 2 \"Fred\""
  {:arglists '([row-value-coll])}
  [& args]
  (generic-1 :values args))

(defn on-conflict
  [& args]
  (generic :on-conflict args))

(defn on-constraint
  "Accepts a single constraint name."
  {:arglists '([constraint])}
  [& args]
  (generic :on-constraint args))

(defn do-nothing
  "Called with no arguments, produces DO NOTHING"
  {:arglists '([])}
  [& args]
  (generic :do-nothing args))

(defn do-update-set
  [& args]
  (generic :do-update-set args))

(defn returning
  "Accepts any number of column names to return from an
  insert operation:

  (returning :*)

  Produces: RETURNING *"
  [& cols]
  (generic :returning cols))

;; helpers that produce non-clause expressions -- must be listed below:
(defn composite
  "Accepts any number of SQL expressions and produces
  a composite value from them:

  (composite :a 42)

  Produces: (a, ?)
  Parameters: 42"
  [& args]
  (into [:composite] args))

;; to make this easy to use in a select, wrap it so it becomes a function:
(defn over
  "Accepts any number of OVER clauses, each of which
  is a pair of an aggregate function and a window function
  or a triple of an aggregate function, a window function,
  and an alias:

  (select :id (over [[:avg :salary] (partition-by :department)]))

  Produces: SELECT id, AVG(salary) OVER ()PARTITION BY department)"
  [& args]
  [(into [:over] args)])

;; this helper is intended to ease the migration from nilenso:
(defn upsert
  "Provided purely to ease migration from nilenso/honeysql-postgres
  this accepts a single clause, constructed from on-conflict,
  do-nothing or do-update-set, and where. Any of those are optional.

  This helper unpacks that clause and turns it into what HoneySQL
  2.x expects, with any where clause being an argument to the
  do-update-set helper, along with the `:fields`.

  nilenso/honeysql-postgres:

  (-> ...
      (upsert (-> (on-conflict :col)
                  do-nothing)))
  (-> ...
      (upsert (-> (on-conflict :col)
                  (do-update-set :x)
                  (where [:<> :x nil]))))

  HoneySQL 2.x:

  (-> ...
      (on-conflict :col)
      do-nothing)
  (-> ...
      (on-conflict :col)
      (do-update-set {:fields [:x]
                      :where [:<> :x nil]}))

  Alternative structure for that second one:

  (-> ...
      (on-conflict :col)
      (do-update-set :x {:where [:<> :x nil]}))"
  ([clause] (upsert {} clause))
  ([data clause]
   (let [{:keys [on-conflict on-constraint do-nothing do-update-set where]} clause]
     (cond-> data
       on-conflict
       (assoc :on-conflict on-conflict)
       on-constraint
       (assoc :on-constraint on-constraint)
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
