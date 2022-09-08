;; copyright (c) 2020-2022 sean corfield, all rights reserved

(ns honey.sql.helpers
  "Helper functions for the built-in clauses in honey.sql.

  All helper functions are inherently variadic. Typical
  usage is threaded, like this:

```
  (-> (select :a :b :c)
      (from :table)
      (where [:= :id 42])
      (sql/format))
```

  Therefore all helpers can take an existing DSL expression
  as their first argument or, if the first argument is not
  a hash map, an empty DSL is assumed -- an empty hash map.
  The above is therefore equivalent to:

```
  (-> {}
      (select :a :b :c)
      (from :table)
      (where [:= :id 42])
      (sql/format))
```

  Some of the helper functions here have `:arglists` metadata
  in an attempt to provide better hints for auto-complete in
  editors but those `:arglists` _always omit the DSL argument_
  to avoid duplicating the various argument lists. When you
  see an auto-complete suggestion like:

    bulk-collect-into [varname] [varname n]

  bear in mind that a DSL hash map can always be threaded in
  so the following (pseudo) arities are also available:

    bulk-collect-into [dsl varname] [dsl varname n]

  The actual arguments are:

    bulk-collect-info [& args]

  (as they are for all helper functions)."
  (:refer-clojure :exclude [filter for group-by into partition-by set update])
  (:require [clojure.core :as c]
            [honey.sql]))

;; implementation helpers:

(defn- default-merge [current args]
  (c/into (vec current) args))

(defn- sym->kw
  "Given a symbol, produce a keyword, retaining the namespace
  qualifier, if any."
  [s]
  (if (symbol? s)
    (if-let [n (namespace s)]
      (keyword n (name s))
      (keyword (name s)))
    s))

(defn- conjunction?
  [e]
  (and (ident? e)
       (contains? #{:and :or} (sym->kw e))))

(defn- simplify-logic
  "For Boolean expressions, simplify the logic to make
  the output expression less nested. Finding :and or
  :or with a single condition can be lifted. Finding
  a conjunction inside the same conjunction can be
  merged.
  Always called on an expression that begins with a conjunction!"
  [e]
  (if (= 1 (count (rest e)))
    (fnext e)
    (let [conjunction (sym->kw (first e))]
      (reduce (fn [acc e]
                (if (and (sequential? e)
                         (conjunction? (first e))
                         (= conjunction (sym->kw (first e))))
                  (c/into acc (rest e))
                  (conj acc e)))
              [conjunction]
              (rest e)))))

(defn- conjunction-merge
  "Merge for where/having. We ignore nil expressions.
  By default, we combine with AND unless the new expression
  begins with a conjunction, in which case use that to
  combine the new expression. Then we perform some
  simplifications to reduce nesting."
  [current args]
  (let [args (remove nil? args)
        [conjunction args]
        (cond (conjunction? (first args))
              [(first args) (rest args)]
              (ident? (first args))
              [:and [args]]
              :else
              [:and args])]
    (if (seq args)
      (-> [conjunction]
          (cond-> (seq current) (conj current))
          (c/into args)
          (simplify-logic))
      current)))

(defn- select-distinct-on-merge
  "Since the first argument in a group is special, we
  need to merge that, and then merge the other args."
  [[c-on & current] [a-on & args]]
  (-> (c/into (vec c-on) a-on)
      (vector)
      (c/into current)
      (c/into args)))

(def ^:private special-merges
  "Identify the conjunction merge clauses."
  {:select-distinct-on #'select-distinct-on-merge
   :where              #'conjunction-merge
   :having             #'conjunction-merge})

(defn- helper-merge [data k args]
  (if-let [merge-fn (special-merges k)]
    (if-let [clause (merge-fn (get data k) args)]
      (assoc data k clause)
      data)
    (clojure.core/update data k default-merge args)))

(defn- generic [k args]
  (if (map? (first args))
    (let [[data & args] args]
      (helper-merge data k args))
    (helper-merge {} k args)))

(defn- generic-1 [k [data arg]]
  (if (map? data)
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
  "Takes a single column name and an optional
  flag to trigger IF EXISTS (use with `alter-table`).

  (alter-table :foo (drop-column :bar))
  (alter-table :foo (drop-column :if-exists :bar))"
  {:arglists '([col] [if-exists col])}
  [& args]
  (generic :drop-column args))

(defn alter-column
  "Like add-column, accepts any number of SQL elements
  that describe the new column definition:

  (alter-column :name [:varchar 64] [:not nil])"
  [& col-elems]
  (generic :alter-column col-elems))

(defn modify-column
  "Like add-column, accepts any number of SQL elements
  that describe the new column definition:

  (modify-column :name [:varchar 64] [:not nil])

  MySQL-specific, deprecated. Use `alter-column` and
  specify the MySQL dialect to get `MODIFY COLUMN`."
  [& col-elems]
  (generic :modify-column col-elems))

(defn rename-column
  "Accepts two column names: the original name and the
  new name to which it should be renamed, and an optional
  flag to trigger IF EXISTS

  (rename-column :name :full-name)
  (rename-column :name :full-name :if-exists)"
  {:arglists '([old-col new-col] [old-col new-col if-exists])}
  [& args]
  (generic :rename-column args))

(defn clear-column
  "Accepts a column name, a partition and an optional
  flag to trigger IF EXISTS

  (clear-column :name :partition_name)
  (clear-column :name :partition_name :if-exists)"
  {:arglists '([old-col new-col] [old-col new-col if-exists])}
  [& args]
  (generic :clear-column args))

(defn comment-column
  "Accepts a column name, a comment and an optional
  flag to trigger IF EXISTS

  (comment-column :name \"Text comment\")
  (comment-column :name \"Text comment\" :if-exists)"
  {:arglists '([old-col new-col] [old-col new-col if-exists])}
  [& args]
  (generic :comment-column args))


(defn alter-partition
  "Accepts a partition name and a partition operation. An option map
  can be passed to enable passing of extra params.

  (alter-partition :partition_expr :attach)
  (alter-partition :partition_expr :move {:to-table :table_dest})

  Produces
  ATTACH PARTITION partition_expr
  MOVE PARTITION partition_expr TO TABLE table_dest"
  {:arglists '([partition operation] [partition operation more])}
  [& args]
  (generic :alter-partition args))

(defn alter-setting
  "Accepts an operation and a sequence of vectors of two children each.
  The first child is converted to a setting name and the second child
  the value of the setting.

  (alter-setting :modify [[:max_part_loading_threads 8] [:max_parts_in_total 500]])
  (alter-setting :replace [[:max_parts_in_total] [:max_part_loading_threads]])

  Produces
  MODIFY SETTING max_part_loading_threads=8, max_parts_in_total=500
  REPLACE SETTING max_parts_in_total, max_part_loading_threads"
  {:arglists '([operation [name value]] [operation [name value]])}
  [& args]
  (generic :alter-setting args))

(defn alter-user
  "Accepts a user name to change and optionally a
  flag to trigger IF EXISTS:

  (alter-user :name)
  (alter-user :if-exists :name)"
  {:arglists '([if-exists name])}
  [& args]
  (generic :alter-user args))

(defn alter-quota
  "Accepts a quota to change and optionally a
  flag to trigger IF EXISTS:

  (alter-quota :quota)
  (alter-quota :if-exists :quota)"
  {:arglists '([if-exists quota])}
  [& args]
  (generic :alter-quota args))

(defn alter-role
  "Accepts a role to change and optionally a
  flag to trigger IF EXISTS:

  (alter-role :role)
  (alter-role :if-exists :role)"
  {:arglists '([if-exists role])}
  [& args]
  (generic :alter-role args))

(defn alter-policy
  "Accepts a row policy to change and optionally a
  flag to trigger IF EXISTS:

  (alter-policy :policy)
  (alter-policy :if-exists :policy)"
  {:arglists '([if-exists policy])}
  [& args]
  (generic :alter-policy args))

(defn alter-settings-profile
  "Accepts a setting profile to change and optionally a
  flag to trigger IF EXISTS:

  (alter-settings-profile :profile)
  (alter-settings-profile :if-exists :profile)"
  {:arglists '([if-exists profile])}
  [& args]
  (generic :alter-settings-profile args))

(defn add-index
  "Like add-column, this accepts any number of SQL
  elements that describe a new index to be added:

  (add-index :unique :name-key :first-name :last-name)

  Produces: UNIQUE name_key(first_name, last_name)"
  {:arglists '([& index-elems])}
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
  {:arglists '([new-table])}
  [& args]
  (generic-1 :rename-table args))

(defn rename-type
  "Requires three arguments, the entity type, previous name
  and the name to rename to. This is currently intended for
  Clickhouse databases.

  (rename-type :dictionary :previous :after)

  Produces: RENAME DICTIONARY previous TO after"
  {:arglists '([type previous after])}
  [& args]
  (generic :rename-type args))

(defn create-database
  "Accepts a database name to create and optionally a
  flag to trigger IF NOT EXISTS:

  (create-database :foo)
  (create-database :foo :if-not-exists)"
  [& args]
  (generic :create-database args))

(defn create-user
  "Accepts a user name to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-user :foo)
  (create-user :foo :if-not-exists)
  (create-user :foo :or-replace)"
  [& args]
  (generic :create-user args))

(defn create-role
  "Accepts a role to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-role :role)
  (create-role :role :if-not-exists)
  (create-role :role :or-replace)"
  [& args]
  (generic :create-role args))

(defn create-row-policy
  "Accepts a row policy to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-row-policy :policy)
  (create-row-policy :policy :if-not-exists)
  (create-row-policy :policy :or-replace)"
  [& args]
  (generic :create-row-policy args))

(defn create-quota
  "Accepts a quota to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-quota :quota)
  (create-quota :quota :if-not-exists)
  (create-quota :quota :or-replace)"
  [& args]
  (generic :create-quota args))

(defn create-settings-profile
  "Accepts a quota to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-settings-profile :profile)
  (create-settings-profile :profile :if-not-exists)
  (create-settings-profile :profile :or-replace)"
  [& args]
  (generic :create-settings-profile args))

(defn create-dictionary
  "Accepts a dictionary to create and optionally a
  flag to trigger IF NOT EXISTS/OR REPLACE:

  (create-dictionary :dictionary)
  (create-dictionary :dictionary :if-not-exists)
  (create-dictionary :or-replace :dictionary :if-not-exists)
  (create-dictionary :dictionary :or-replace)"
  [& args]
  (generic :create-dictionary args))

(defn create-table
  "Accepts a table name to create and optionally a
  flag to trigger IF NOT EXISTS in the SQL:

  (create-table :foo)
  (create-table :foo :if-not-exists)"
  {:arglists '([table] [table if-not-exists])}
  [& args]
  (generic :create-table args))

(defn create-table-as
  "Accepts a table name to create and optionally a
  flag to trigger IF NOT EXISTS in the SQL:

  (create-table-as :foo)
  (create-table-as :foo :if-not-exists)"
  {:arglists '([table] [table if-not-exists])}
  [& args]
  (generic :create-table-as args))

(defn create-extension
  "Accepts an extension name to create and optionally a
  flag to trigger IF NOT EXISTS in the SQL:

  (create-extension :postgis)
  (create-extension :postgis :if-not-exists)"
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
  ;; special cases so (with-columns [[:col-1 :definition] [:col-2 :definition]])
  ;; also works in addition to (with-columns [:col-1 :definition] [:col-2 :definition])
  (cond (and (= 1 (count args)) (sequential? (first args)) (sequential? (ffirst args)))
        (generic :with-columns (cons {} (first args)))
        (and (= 2 (count args))
             (map? (first args))
             (sequential? (second args))
             (sequential? (first (second args))))
        (generic :with-columns (cons (first args) (second args)))
        :else
        (generic :with-columns args)))

(defn create-view
  "Accepts a single view name to create.

  (-> (create-view :cities)
      (select :*) (from :city))"
  {:arglists '([view])}
  [& args]
  (generic :create-view args))

(defn create-materialized-view
  "Accepts a single view name to create.

  (-> (create-materialized-view :cities)
      (select :*) (from :city))
      (with-data true)"
  {:arglists '([view])}
  [& args]
  (generic :create-materialized-view args))

(defn create-live-view
  "Accepts a single view name to create.

  (-> (create-live-view :cities)
      (select :*) (from :city))
      (with-data true)"
  {:arglists '([view])}
  [& args]
  (generic :create-live-view args))

(defn create-window-view
  "Accepts a single view name to create.

  (-> (create-window-view :cities)
      (select :*) (from :city))
      (with-data true)"
  {:arglists '([view])}
  [& args]
  (generic :create-window-view args))

(defn create-function
  "Accepts a single function name to create.

  (-> (create-function :cities)
      (select :*) (from :city))
      (with-data true)"
  {:arglists '([function])}
  [& args]
  (generic :create-function args))

(defn drop-table
  "Accepts one or more table names to drop.

  (drop-table :foo)"
  [& tables]
  (generic :drop-table tables))

(defn drop-extension
  "Accepts one or more extension names to drop."
  [& extensions]
  (generic :drop-extension extensions))

(defn drop-view
  "Accepts one or more view names to drop."
  [& views]
  (generic :drop-view views))

(defn drop-materialized-view
  "Accepts one or more materialied view names to drop."
  [& views]
  (generic :drop-materialized-view views))

(defn drop-database
  "Accepts a database name to drop.

  (drop-database :foo)"
  [& database]
  (generic :drop-database database))

(defn drop-dictionary
  "Accepts a dictionary name to drop.

  (drop-dictionary :foo)"
  [& database]
  (generic :drop-dictionary database))

(defn drop-user
  "Accepts a user name to drop.

  (drop-user :foo)"
  [& database]
  (generic :drop-user database))

(defn drop-role
  "Accepts a role name to delete.

  (drop-role :foo)"
  [& database]
  (generic :drop-role database))

(defn drop-quota
  "Accepts a quota name to delete.

  (drop-quota :foo)"
  [& database]
  (generic :drop-quota database))

(defn drop-function
  "Accepts a function name to delete.

  (drop-function :foo)"
  [& database]
  (generic :drop-function database))

(defn drop-row-policy
  "Accepts a row policy to delete.

  (drop-row-policy :foo)"
  [& database]
  (generic :drop-row-policy database))

(defn drop-settings-profile
  "Accepts a settings profile to delete.

  (drop-settings-profile :foo)"
  [& database]
  (generic :drop-settings-profile database))

(defn refresh-materialized-view
  "Accepts a materialied view name to refresh."
  {:arglists '([view])}
  [& views]
  (generic :refresh-materialized-view views))

(defn with
  "Accepts one or more CTE definitions.

  See the documentation for the `:with` clause."
  [& args]
  (generic :with args))

(defn with-recursive
  "Accepts one or more CTE definitions.

  See the documentation for the `:with` clause."
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

(comment
  (= (select-distinct-on [:a :b] :c [:d :dd])
     (-> (select-distinct-on [:a] :c)
         (select-distinct-on [:b] [:d :dd])))
  )

(defn select-top
  "Accepts a TOP expression, followed by any number of
  column names, or column/alias pairs, or SQL expressions
  (optionally aliased), as for `select`. The TOP expression
  can be a simple numeric expression, or a sequence with
  a numeric expression followed by keywords (or symbols)
  for PERCENT and/or WITH TIES."
  [& args]
  (generic :select-top args))

(defn select-distinct-top
  "Like `select-top` but produces SELECT DISTINCT TOP..."
  [& args]
  (generic :select-distinct-top args))

(defn into
  "Accepts table name, optionally followed a database name."
  {:arglists '([table] [table dbname])}
  [& args]
  (generic :into args))

(defn bulk-collect-into
  "Accepts a variable name, optionally followed by a limit
  expression."
  {:arglists '([varname] [varname n])}
  [& args]
  (generic :bulk-collect-into args))

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
  (let [[data & args :as args']
        (if (map? (first args)) args (cons {} args))
        [table cols statement] args]
    (if (and (sequential? cols) (map? statement))
      (generic :insert-into [data [table cols] statement])
      (generic :insert-into args'))))

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

(defn delete-where
  "For deleting data matching the specified filtering
  expression. Accepts a single filtering expression.
  This is intended for clickhouse databases and does not
  refer to the sql `delete .... where condition`. In clickhouse
  the expression must be of type UInt8

  (-> (delete-where 233))

  Produces
  DELETE WHERE 233"
  {:arglists '([table])}
  [& args]
  (generic :delete-where args))

(defn truncate
  "Accepts a single table name to truncate."
  {:arglists '([table])}
  [& args]
  (generic-1 :truncate args))

(defn truncate-if-exists
  "Accepts a single table name to truncate and an optional
  flag to trigger IF EXISTS.
  This function is intended for Clickhouse."
  {:arglists '([table] [if-exists table])}
  [& args]
  (generic :truncate-if-exists args))

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
  "Accepts similar arguments to `select` as part of
  a SQL `USING` clause."
  [& args]
  (generic :using args))

(defn join-by
  "Accepts a sequence of join clauses to be generated
  in a specific order.

  (-> (select :*)
      (from :foo)
      (join-by :left [:bar [:= :foo.id :bar.id]]
               :join [:quux [:= :bar.qid :quux.id]]))

  This produces a LEFT JOIN followed by an INNER JOIN
  even though the 'natural' order for `left-join` and
  `join` would be to generate the INNER JOIN first,
  followed by the LEFT JOIN."
  [& args]
  (generic :join-by args))

(defn join
  "Accepts one or more (INNER) JOIN expressions. Each
  join expression is specified as a pair of arguments,
  where the first one is the table name (or a pair of
  table and alias) and the second one is the join
  condition:

  (join :table [:= :foo.id :table.foo_id])
  (join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  INNER JOIN table ON foo.id = table.foo_id
  INNER JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :join args))

(defn left-join
  "Accepts one or more LEFT JOIN expressions. Each
  join expression is specified as a pair of arguments,
  where the first one is the table name (or a pair of
  table and alias) and the second one is the join
  condition:

  (left-join :table [:= :foo.id :table.foo_id])
  (left-join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  LEFT JOIN table ON foo.id = table.foo_id
  LEFT JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :left-join args))

(defn right-join
  "Accepts one or more RIGHT JOIN expressions. Each
  join expression is specified as a pair of arguments,
  where the first one is the table name (or a pair of
  table and alias) and the second one is the join
  condition:

  (right-join :table [:= :foo.id :table.foo_id])
  (right-join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  RIGHT JOIN table ON foo.id = table.foo_id
  RIGHT JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :right-join args))

(defn inner-join
  "An alternative name to `join`, this accepts one or
  more INNER JOIN expressions. Each join expression
  is specified as a pair of arguments, where the
  first one is the table name (or a pair of table
  and alias) and the second one is the join condition:

  (inner-join :table [:= :foo.id :table.foo_id])
  (inner-join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  INNER JOIN table ON foo.id = table.foo_id
  INNER JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :inner-join args))

(defn outer-join
  "Accepts one or more OUTER JOIN expressions. Each
  join expression is specified as a pair of arguments,
  where the first one is the table name (or a pair of
  table and alias) and the second one is the join
  condition:

  (outer-join :table [:= :foo.id :table.foo_id])
  (outer-join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  OUTER JOIN table ON foo.id = table.foo_id
  OUTER JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :outer-join args))

(defn full-join
  "Accepts one or more FULL JOIN expressions. Each
  join expression is specified as a pair of arguments,
  where the first one is the table name (or a pair of
  table and alias) and the second one is the join
  condition:

  (full-join :table [:= :foo.id :table.foo_id])
  (full-join [:table :t] [:= :foo.id :t.foo_id])

  Produces:
  FULL JOIN table ON foo.id = table.foo_id
  FULL JOIN table AS t ON foo.id = t.foo_id"
  [& args]
  (generic :full-join args))

(defn cross-join
  "Accepts one or more CROSS JOIN expressions. Each
  cross join expression is specified as a table
  name (or a pair of table and alias):

  (cross-join :table)
  (cross-join [:table :t])

  Produces:
  CROSS JOIN table
  CROSS JOIN table AS t"
  [& args]
  (generic :cross-join args))

(defn prewhere
  "Accepts a Clickhouse database pre-where expression.
  An expression is specified as a pair of arguments,
  where the first one is the column name (or a sequence of
  column names) and the second one is a hash map representing
  a SQL statement:

  (prewhere [:v.a :v.b] {:select [:a :b], :from [:table], :where [:= :camp :que]})
  (prewhere :v.a {:select [:a], :from [:table], :where [:= :camp :que]})

  Produces:
  PREWHERE(v.a, v.b) IN (SELECT a, b FROM table WHERE camp = que)
  PREWHERE v.a IN (SELECT a FROM table WHERE camp = que)"
  [& exprs]
  (generic :prewhere exprs))

(defn where
  "Accepts one or more SQL expressions (conditions) and
  combines them with AND (by default):

  (where [:= :status 0] [:<> :task \"backup\"])
  or:
  (where :and [:= :status 0] [:<> :task \"backup\"])

  Produces: WHERE (status = ?) AND (task <> ?)
  Parameters: 0 \"backup\"

  For a single expression, the brackets can be omitted:

  (where := :status 0) ; same as (where [:= :status 0])

  With multiple expressions, the conjunction may be
  specified as a leading symbol:

  (where :or [:= :status 0] [:= :task \"stop\"])

  Produces: WHERE (status = 0) OR (task = ?)
  Parameters: 0 \"stop\""
  [& exprs]
  (generic :where exprs))

(defn group-by
  "Accepts one or more SQL expressions to group by.

  (group-by :foo :bar)
  (group-by [:date :baz])

  Produces:
  GROUP BY foo, bar
  GROUP BY DATE(baz)"
  [& args]
  (generic :group-by args))

(defn having
  "Like `where`, accepts one or more SQL expressions
  (conditions) and combines them with AND (by default):

  (having [:> :count 0] [:<> :name nil])
  or:
  (having :and [:> :count 0] [:<> :name nil])

  Produces: HAVING (count > ?) AND (name IS NOT NULL)
  Parameters: 0

  (having :> :count 0)

  Produces: HAVING count > ?
  Parameters: 0

  (having :or [:> :count 0] [:= :name \"\"])

  Produces: HAVING (count > ?) OR (name = ?)
  Parameters: 0 \"\""
  [& exprs]
  (generic :having exprs))

(defn window
  "Accepts a window name followed by a partition by clause."
  [& args]
  (generic :window args))

(defn partition-by
  "Accepts one or more columns or SQL expressions to
  partition by as part of a `WINDOW` expression."
  [& args]
  (generic :partition-by args))

(defn order-by
  "Accepts one or more expressions to order by.

  An ordering expression may be a simple column name
  which is assumed to be ordered `ASC`, or a pair of
  an expression and a direction (`:asc` or `:desc`):

  (order-by :foo)
  (order-by [:bar :desc])
  (order-by [[:date :baz] :asc])

  Produces:
  ORDER BY foo ASC
  ORDER BY bar DESC
  ORDER BY DATE(baz) ASC"
  [& args]
  (generic :order-by args))

(defn limit
  "Specific to some databases (notabley MySQL),
  accepts a single SQL expression:

  (limit 40)

  Produces: LIMIT ?
  Parameters: 40

  The two-argument syntax is not supported unless using Clickhouse:
  use `offset` instead:

  `LIMIT 20,10` is equivalent to `LIMIT 10 OFFSET 20`

  (-> (limit 10) (offset 20))

  For clickhouse dialect:
  (limit [2, 10 :with-ties])

  Produces: LIMIT 2, 10 WITH TIES
  Parameters: 2, 10
  "
  {:arglists '([limit])}
  [& args]
  (generic-1 :limit args))

(defn limit-by
  "Specific to Clickhouse,
  accepts a single SQL expression:

  (limit-by 40 :id)
  (limit-by [10, 40] :id)
  (limit-by [10, 40] [:id :ds])

  Produces:
   LIMIT 40 BY id
   LIMIT 10, 40 BY id
   LIMIT 10, 40 BY id, ds
  Parameters:
   40
   10, 40"
  ;{:arglists '([limit-by])}
  [& args]
  (generic :limit-by args))

(defn offset
  "Accepts a single SQL expression:

  (offset 10)

  Produces: OFFSET ?
  Parameters: 10"
  {:arglists '([offset])}
  [& args]
  (generic-1 :offset args))

(defn fetch
  "Accepts a single SQL expression:

  (fetch 10)

  Produces: FETCH ? ONLY
  Parameters: 10

  When using the Clickhouse dialect, the expression provided can be a
  vector which contains a number as the first item and a key as the second.
  The second expression, a modifier replaces the `only` option.

  (fetch [10 :rows-with-ties])

  Produces: FETCH ? ONLY
  Parameters: 10
  "
  {:arglists '([limit])}
  [& args]
  (generic-1 :fetch args))

(defn for
  "Accepts a lock strength, optionally followed by one or
  more table names, optionally followed by a qualifier."
  {:arglists '([lock-strength table* qualifier*])}
  [& args]
  (generic-1 :for args))

(defn lock
  "Intended for MySQL, this accepts a lock mode.

  It will accept the same type of syntax as `for` even
  though MySQL's `lock` clause is less powerful."
  {:arglists '([lock-mode])}
  [& args]
  (generic-1 :lock args))

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
  "Accepts zero or more SQL entities (keywords or symbols),
  optionally followed by a single SQL clause (hash map)."
  {:arglists '([column* where-clause])}
  [& args]
  (generic :on-conflict args))

(defn on-constraint
  "Accepts a single constraint name."
  {:arglists '([constraint])}
  [& args]
  (generic-1 :on-constraint args))

(defn do-nothing
  "Called with no arguments, produces DO NOTHING"
  {:arglists '([])}
  [& args]
  (generic :do-nothing args))

(defn do-update-set
  "Accepts one or more columns to update, or a hash map
  of column/value pairs (like `set`), optionally followed
  by a `WHERE` clause. Can also accept a single hash map
  with a `:fields` entry specifying the columns to update
  and a `:where` entry specifying the `WHERE` clause."
  {:arglists '([field-where-map] [column-value-map] [column* opt-where-clause])}
  [& args]
  (generic :do-update-set args))

(defn on-duplicate-key-update
  "MySQL's upsert facility. Accepts a hash map of
  column/value pairs to be updated (like `set` does)."
  {:arglists '([column-value-map])}
  [& args]
  (generic :on-duplicate-key-update args))

(defn returning
  "Accepts any number of column names to return from an
  insert operation:

  (returning :*) and (returning :a :b)

  Produce: RETURNING * and RETURNING a, b respectively."
  [& cols]
  (generic :returning cols))

(defn table
  "Accepts a single table name and produces TABLE name

  This is equivalent to: SELECT * FROM name"
  {:arglists '([name])}
  [& args]
  (generic-1 :table args))

(defn with-data
  "Accepts a Boolean determining WITH DATA vs WITH NO DATA."
  {:arglists '([data?])}
  [& args]
  (generic-1 :with-data args))

;; helpers that produce non-clause expressions -- must be listed below:
(defn composite
  "Accepts any number of SQL expressions and produces
  a composite value from them:

  (composite :a 42)

  Produces: (a, ?)
  Parameters: 42"
  [& args]
  (c/into [:composite] args))

(defn filter
  "Accepts alternating expressions and clauses and
  produces a FILTER expression:

  (filter :%count.* (where :> i 5))

  Produces: COUNT(*) FILTER (WHERE i > ?)
  Parameters: 5"
  {:arglists '([expr1 clause1 & more])}
  [& args]
  (c/into [:filter] args))

(defn lateral
  "Accepts a SQL clause or a SQL expression:

  (lateral (-> (select '*) (from 'foo)))
  (lateral '(calc_value bar))

  Produces:
  LATERAL (SELECT * FROM foo)
  LATERAL CALC_VALUE(bar)"
  {:arglists '([clause-or-expression])}
  [& args]
  (c/into [:lateral] args))

;; to make this easy to use in a select, wrap it so it becomes a function:
(defn over
  "Accepts any number of OVER clauses, each of which
  is a pair of an aggregate function and a window function
  or a triple of an aggregate function, a window function,
  and an alias:

  (select :id (over [[:avg :salary] (partition-by :department)]))

  Produces: SELECT id, AVG(salary) OVER ()PARTITION BY department)"
  [& args]
  [(c/into [:over] args)])

(defn within-group
  "Accepts alternating expressions and clauses and
  produces a WITHIN GROUP expression:

  (within-group :%count.* (where :> i 5))

  Produces: COUNT(*) WITHIN GROUP (WHERE i > ?)
  Parameters: 5"
  {:arglists '([expr1 clause1 & more])}
  [& args]
  (c/into [:within-group] args))

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

(defn sample
  "Accepts similar arguments to `select` as part of
  a Clickhouse Sample clause. Instead of keywords for
  table names like in select, the arguments should be
  decimals or a number.

  (format-sample :sample 0.2)
  (format-sample :sample 100000)
  (format-sample :sample [0.2 0.4])

  Produces:
  SAMPLE 0.2
  SAMPLE 100000
  SAMPLE 0.2 OFFSET 0.4"
  [& args]
  (generic :sample args))

(defn watch
  "This clause returns the WATCH query for clickhouse.

  (watch :db.live_view)

  Produces:
  WATCH db.live_view"
  [& args]
  (generic-1 :watch args))

(defn on-cluster
  "Accepts one expression to create Clickhouse ON CLUSTER clause.

  (on-cluster :cluster)

  Produces:
  ON CLUSTER cluster"
  [& args]
  (generic-1 :on-cluster args))

(defn to-name
  "Accepts one expression to create Clickhouse TO clause.

  (to-name :db.name)

  Produces:
  TO db.name"
  [& args]
  (generic-1 :to-name args))

(defn engine
  "Accepts one expression to create Clickhouse Engine clause.

  (engine :engine)

  Produces:
  ENGINE engine"
  [& args]
  (generic-1 :engine args))

(defn inner-engine
  "Accepts one expression to create Clickhouse INNER ENGINE clause.

  (inner-engine :engine)

  Produces:
  INNER ENGINE = engine"
  [& args]
  (generic-1 :inner-engine args))

(defn watermark
  "Accepts one expression to create Clickhouse WATERMARK clause.

  (watermark :strategy)

  Produces:
  WATERMARK = strategy"
  [& args]
  (generic-1 :watermark args))

(defn allowed-lateness
  "Accepts one expression to create Clickhouse WATERMARK clause.

  (allowed-lateness :allowed-lateness)

  Produces:
  ALLOWED LATENESS = interval"
  [& args]
  (generic-1 :allowed-lateness args))

(defn populate
  "This clause returns the POPULATE clause for clickhouse.

  (populate )

  Produces:
  POPULATE"
  [& args]
  (generic :populate args))

(defn events
  "This clause returns the EVENTS clause for clickhouse.

  (events)

  Produces:
  EVENTS"
  [& args]
  (generic :events args))

(defn clickhouse-comment
  "Accepts one expression to create Clickhouse COMMENT clause.

  (clickhouse-comment \"comment\")

  Produces:
  COMMENT 'comment'"
  [& args]
  (generic-1 :clickhouse-comment args))

(defn modify-comment
  "Accepts a string to create the Clickhouse MODIFY COMMENT clause.

  (modify-comment \"comment\")

  Produces:
  MODIFY COMMENT 'comment'"
  [& args]
  (generic-1 :modify-comment args))

(defn with-timeout
  "Accepts one expression to create Clickhouse WITH TIMEOUT clause.

  (with-timeout :234)

  Produces:
  WITH TIMEOUT 234"
  [& args]
  (generic-1 :with-timeout args))

(defn with-refresh
  "Accepts one expression to create Clickhouse WITH REFRESH clause.

  (with-refresh :234)
  (-> (with-timeout :456) (with-refresh :234))

  Produces:
  WITH REFRESH 234
  WITH TIMEOUT 456 AND REFRESH 234"
  [& args]
  (generic-1 :with-refresh args))

(defn show
  "Accepts expressions to create SHOW statements.
  These are intended for Clickhouse dialect."
  {:arglists '([table name {:keys [create? pre more]}])}
  [& args]
  (generic :show args))

(defn grant
  "Accepts expressions to create GRANT statements.
  These are intended for Clickhouse dialect.

  (grant :privilege)

  Produces:
  GRANT privilege"
  {:arglists '([privilege] [privilege {:keys [pre role user table columns mods]}])}
  [& args]
  (generic :grant args))

(defn revoke
  "Accepts expressions like `grant` to create REVOKE statements.
  These are intended for Clickhouse dialect.

  (revoke :privilege)

  Produces:
  REVOKE privilege"
  {:arglists '([privilege] [privilege {:keys [pre role user table columns mods]}])}
  [& args]
  (generic :revoke args))

(defn attach
  "Accepts expressions to create ATTACH statements.
  These are intended for Clickhouse dialect.

  (attach :type :db.name :if-not-exists)

  Produces:
  ATTACH TYPE IF NOT EXISTS db.name ON CLUSTER cluster_name"
  {:arglists '([table database] [table database if-not-exists])}
  [& args]
  (generic :attach args))

(defn detach
  "Accepts expressions to create ATTACH statements.
  These are intended for Clickhouse dialect.

  (detach :type :if-exists :db.name)

  Produces:
  DETACH TYPE IF EXISTS db.name ON CLUSTER cluster_name"
  {:arglists '([table database] [table database if-not-exists])}
  [& args]
  (generic :detach args))

(defn exists
  "Accepts expressions to create EXISTS statements.
  These are intended for Clickhouse dialect.

  (exists :table :db.name (on-cluster :cluster_name))
  (exists :temporary :dictionary :db.name (on-cluster :cluster_name))

  Produces:
  EXISTS TABLE db.name ON CLUSTER cluster_name
  EXISTS TEMPORARY DICTIONARY db.name ON CLUSTER cluster_name"
  {:arglists '([type name] [temporary type name])}
  [& args]
  (generic :exists args))

(defn explain
  "Accepts expressions to create EXPLAIN statements.
  These are intended for Clickhouse dialect.

  (explain :type {})

  Produces:
  EXPLAIN type"
  {:arglists '([type {:keys [settings data more]}])}
  [& args]
  (generic :explain args))

(defn default-role
  "Accepts a role an optional flag triggering ALL or ALL EXCEPT and
  and extra flag to add `SET` for SET DEFAULT ROLE clause

  (-> (alter-user :user) (default-role :role :all-except))

  Produces:
  ALTER USER user DEFAULT ROLE ALL EXCEPT role"
  {:arglists '([role] [set role] [role flag] [set role flag])}
  [& args]
  (generic :default-role args))

(defn into-outfile
  "Accepts one or more expressions to format to into outfile clause
  for clickhouse.

  The first argument is the filename while the second is a map that
  receives extra options, namely the compression type and level.

  (into-outfile :file)
  (into-outfile :file {:compression :gzip :level 1})

  Produces:
  INTO OUTFILE file
  INTO OUTFILE file COMPRESSION gzip LEVEL 1"
  [& args]
  (generic :into-outfile args))

(defn clickhouse-format
  "Accepts one expression.

  (clickhouse-format :CSV)

  Produces:
  FORMAT CSV"
  [& args]
  (generic-1 :clickhouse-format args))

(defn generic-helper-variadic
  "Most clauses that accept a sequence of items can be implemented
  using this helper, as:

  (defn my-helper [& args] (generic-helper-variadic :my-clause args))"
  [k args]
  (generic k args))

(defn generic-helper-unary
  "Clauses that accept only a single item can be implemented
  using this helper, as:

  (defn my-helper [& args] (generic-helper-unary :my-clause args))

  Even though your helper is designed for clauses that accept
  only a single item, you should still define it as variadic,
  because that is the convention all helpers use here."
  [k args]
  (generic-1 k args))

#?(:clj
   (do
     ;; #409 this assert is only valid when :doc metadata is not elided:
     (when (-> #'generic-helper-unary meta :doc)
       ;; ensure #295 stays true (all public functions have docstring):
       (assert (empty? (->> (ns-publics *ns*) (vals) (c/filter (comp not :doc meta))))))
     ;; ensure all public functions match clauses:
     (assert (= (clojure.core/set (conj @#'honey.sql/default-clause-order
                                        :composite :filter :lateral :over :within-group
                                        :upsert
                                        :generic-helper-variadic :generic-helper-unary))
                (clojure.core/set (conj (map keyword (keys (ns-publics *ns*)))
                                        :nest :raw))))))
