# SQL Clauses Supported

This section lists all the SQL clauses that HoneySQL
supports out of the box, in the order that they are
processed for formatting (except for some natural
grouping of related clauses).

Clauses can be specified as keywords or symbols. Use
`-` in the clause name where the formatted SQL would have
a space (e.g., `:left-join` is formatted as `LEFT JOIN`).

Except as noted, these clauses apply to all the SQL
dialects that HoneySQL supports. See also the section on
[database-specific hints and tips](databases.md).

DDL clauses are listed first, followed by SQL clauses.

The examples herein assume:
```clojure
(refer-clojure :exclude '[partition-by])
(require '[honey.sql :as sql]
         '[honey.sql.helpers :as h :refer [select from join-by left-join join
                                           where order-by over partition-by window]])
```

Every DDL and SQL clause has a corresponding helper function
in `honey.sql.helpers`. In general, `(helper :foo expr)` will
produce `{:helper [:foo expr]}` (with a few exceptions -- see
the docstring of the helper function for details).

# DDL Clauses

HoneySQL supports the following DDL clauses as a data DSL.

Several of these include column specifications and HoneySQL
provides some special syntax (functions) to support that.
See [Column Descriptors in Special Syntax](special-syntax.md#column-descriptors) for more details.

> Google BigQuery support: `[:bigquery/array :string]` as a column type produces `ARRAY<STRING>` and `[:bigquery/struct col1-spec col2-spec]` as a column type produces `STRUCT<col1, col2>` (where `colN-spec` is a vector specifying a named column).

## alter-table, add-column, drop-column, alter-column, modify-column, rename-column

`:alter-table` can accept either a single table name or
a sequence that begins with a table name and is followed
by clauses that manipulate columns (or indices, see below).

If a single table name is provided, a single column
(or index) operation can provided in the hash map DSL:

```clojure
user=> (sql/format {:alter-table :fruit
                    :add-column [:id :int [:not nil]]})
["ALTER TABLE fruit ADD COLUMN id INT NOT NULL"]
user=> (sql/format {:alter-table :fruit
                    :add-column [:id :int [:not nil] :if-not-exists]})
["ALTER TABLE fruit ADD COLUMN IF NOT EXISTS id INT NOT NULL"]
user=> (sql/format {:alter-table :fruit
                    :drop-column :ident})
["ALTER TABLE fruit DROP COLUMN ident"]
user=> (sql/format {:alter-table :fruit
                    :drop-column [:if-exists :ident]})
["ALTER TABLE fruit DROP COLUMN IF EXISTS ident"]
user=> (sql/format {:alter-table :fruit
                    :alter-column [:id :int :unsigned nil]})
["ALTER TABLE fruit ALTER COLUMN id INT UNSIGNED NULL"]
user=> (sql/format {:alter-table :fruit
                    :rename-column [:look :appearance]})
["ALTER TABLE fruit RENAME COLUMN look TO appearance"]
```

If a sequence of a table name and various clauses is
provided, the generated `ALTER` statement will have
comma-separated clauses:

```clojure
user=> (sql/format {:alter-table [:fruit
                                  {:add-column [:id :int [:not nil]]}
                                  {:drop-column :ident}]})
["ALTER TABLE fruit ADD COLUMN id INT NOT NULL, DROP COLUMN ident"]
user=> (sql/format {:alter-table [:fruit
                                  {:add-column [:id :int [:not nil]]}
                                  {:add-column [:name [:varchar 32]]}
                                  {:drop-column :ident}
                                  {:alter-column [:appearance :text]}]})
["ALTER TABLE fruit ADD COLUMN id INT NOT NULL, ADD COLUMN name VARCHAR(32), DROP COLUMN ident, ALTER COLUMN appearance TEXT"]
user=> (sql/format {:alter-table [:fruit
                                  {:add-column [:id :int [:not nil] :if-not-exists]}
                                  {:drop-column [:if-exists :ident]}]})
["ALTER TABLE fruit ADD COLUMN IF NOT EXISTS id INT NOT NULL, DROP COLUMN IF EXISTS ident"]
```

As can be seen above, `:add-column` and `:alter-column`
both accept a column description (as a sequence of simple
expressions); `:drop-column` accepts one or more column names
optionally prefixed by `:if-exists`,
and `:rename-column` accepts a sequence with two column
names: the "from" and the "to" names.

> Note: `:modify-column` is MySQL-specific and should be considered legacy and deprecated. `:alter-column` will produce `MODIFY COLUMN` when the MySQL dialect is selected.

### add-index, drop-index

Used with `:alter-table`,
`:add-index` accepts a single (function) expression
that describes an index, and `:drop-index` accepts a
single index name:

```clojure
user=> (sql/format {:alter-table :fruit
                    :add-index [:index :look :appearance]})
["ALTER TABLE fruit ADD INDEX look(appearance)"]
user=> (sql/format {:alter-table :fruit
                    :add-index [:unique nil :color :appearance]})
["ALTER TABLE fruit ADD UNIQUE(color, appearance)"]
user=> (sql/format {:alter-table :fruit :drop-index :look})
["ALTER TABLE fruit DROP INDEX look"]
```

You can use `:add-index` to add a primary key to an existing table, as follows:

```clojure
user=> (-> (h/alter-table :fruit)
           (h/add-index :primary-key :id)
           (sql/format))
["ALTER TABLE fruit ADD PRIMARY KEY(id)"]
```

### rename-table

Used with `:alter-table`,
`:rename-table` accepts a single table name:

```clojure
user=> (sql/format {:alter-table :fruit :rename-table :vegetable})
["ALTER TABLE fruit RENAME TO vegetable"]
```

> Note: this would be better as `:rename-to` since there is a `RENAME TABLE old_name TO new_name` SQL statement. _[I may yet add a variant to support that specifically]_

## create-table, with-columns

`:create-table` can accept a single table name or a sequence
containing a table name and a flag indicating the creation
should be conditional (`:if-not-exists` or the symbol `if-not-exists`). `:create-table` should
be used with `:with-columns` to specify the actual columns
in the table:

```clojure
user=> (sql/format {:create-table :fruit
                    :with-columns
                    [[:id :int [:not nil]]
                     [:name [:varchar 32] [:not nil]]
                     [:cost :float :null]]})
["CREATE TABLE fruit (id INT NOT NULL, name VARCHAR(32) NOT NULL, cost FLOAT NULL)"]
```

Any keywords (or symbols) preceding the table name will be
turned into SQL keywords (this is true for all of the `create-*`
DSL identifiers):

```clojure
user=> (sql/format {:create-table [:my :fancy :fruit :if-not-exists]
                    :with-columns
                    [[:id :int [:not nil]]
                     [:name [:varchar 32] [:not nil]]
                     [:cost :float :null]]})
["CREATE MY FANCY TABLE IF NOT EXISTS fruit (id INT NOT NULL, name VARCHAR(32) NOT NULL, cost FLOAT NULL)"]
```

This lets you write SQL like `CREATE TEMP TABLE foo ...` etc.

The `:with-columns` clause is formatted as if `{:inline true}`
was specified so nothing is parameterized. In addition,
everything except the first element of a column description
will be uppercased (mostly to give the appearance of separating
the column name from the SQL keywords) -- except for keywords
that start with `'` which will be transcribed into the SQL exactly
as-is, with no case or character conversion at all. This
"escape hatch" is intended to allow for SQL dialects that are
case sensitive and/or have other unusual syntax constraints.

Various function-like expressions can be specified, as shown
in the example above, that allow things like `CHECK` for a
constraint, `FOREIGN KEY` (with a column name), `REFERENCES`
(with a pair of column names). See [Column Descriptors in Special Syntax](special-syntax.md#column-descriptors) for more details.

For example:

```clojure
user=> (-> {:create-table :foo
            :with-columns
            [[:a :int]
             [:b :int]
             [[:primary-key :a :b]]]}
           (sql/format))
["CREATE TABLE foo (a INT, b INT, PRIMARY KEY(a, b))"]
```

or:

```clojure
user=> (-> {:create-table [:bar]
            :with-columns
            [[:a :integer]
             [:b :integer]
             [[:constraint :foo_natural_key] :unique [:composite :a :b]]]}
           (sql/format))
["CREATE TABLE bar (a INTEGER, b INTEGER, CONSTRAINT foo_natural_key UNIQUE (a, b))"]
```

or a mix of column constraints and table constraints:

```clojure
user=> (-> '{create-table quux
             with-columns
             ((a integer (constraint a_pos) (check (> a 0)))
              (b integer)
              ((constraint a_bigger) (check (< b a))))}
           (sql/format {:pretty true}))
["
CREATE TABLE quux
(a INTEGER CONSTRAINT a_pos CHECK(a > 0), b INTEGER, CONSTRAINT a_bigger CHECK(b < a))
"]
```


## create-table-as

`:create-table-as` can accept a single table name or a sequence
that starts with a table name, optionally followed by
a flag indicating the creation should be conditional
(`:if-not-exists` or the symbol `if-not-exists`),
optionally followed by a `{:columns ..}` clause to specify
the columns to use in the created table, optionally followed
by special syntax to specify `TABLESPACE` etc.

For example:

```clojure
user=> (sql/format {:create-table-as [:metro :if-not-exists
                                      {:columns [:foo :bar :baz]}
                                      [:tablespace [:entity :quux]]],
                    :select [:*],
                    :from [:cities],
                    :where [:= :metroflag "y"],
                    :with-data false}
                   {:pretty true})
["
CREATE TABLE IF NOT EXISTS metro (foo, bar, baz) TABLESPACE quux AS
SELECT *
FROM cities
WHERE metroflag = ?
WITH NO DATA
" "y"]
```

Without the `{:columns ..}` clause, the table will be created
based on the columns in the query that follows.

A more concise version of the above can use the `TABLE` clause:


```clojure
user=> (sql/format {:create-table-as [:metro :if-not-exists
                                      {:columns [:foo :bar :baz]}
                                      [:tablespace [:entity :quux]]],
                    :table :cities,
                    :where [:= :metroflag "y"],
                    :with-data false}
                   {:pretty true})
["
CREATE TABLE IF NOT EXISTS metro (foo, bar, baz) TABLESPACE quux AS
TABLE cities
WHERE metroflag = ?
WITH NO DATA
" "y"]
```

As above, any keywords (or symbols) preceding the table name
will be turned into SQL keywords (this is true for all of the
`create-*` DSL identifiers) so you can write:

```
{:create-table-as [:temp :metro :if-not-exists [..]] ..}
```

to produce `CREATE TEMP TABLE IF NOT EXISTS metro ..`.

## create-extension

`:create-extension` can accept a single extension name or a
sequence of the extension name, followed by
a flag indicating the creation should be conditional
(`:if-not-exists` or the symbol `if-not-exists`).
See the [PostgreSQL](postgresql.md) section for examples.

## create-view, create-materialized-view, refresh-materialized-view

`:create-view`, `:create-materialized-view`, and
`:refresh-materialized-view` all accept a single view name
or a sequence of optional modifiers, followed by the view name,
followed by a flag indicating the creation should be conditional
(`:if-not-exists` or the symbol `if-not-exists`):

```clojure
user=> (sql/format {:create-view :products
                    :select [:*]
                    :from [:items]
                    :where [:= :category "product"]})
["CREATE VIEW products AS SELECT * FROM items WHERE category = ?" "product"]
user=> (sql/format {:create-view [:products :if-not-exists]
                    :select [:*]
                    :from [:items]
                    :where [:= :category "product"]})
["CREATE VIEW IF NOT EXISTS products AS SELECT * FROM items WHERE category = ?" "product"]
user=> (sql/format {:refresh-materialized-view [:concurrently :products]
                    :with-data false})
["REFRESH MATERIALIZED VIEW CONCURRENTLY products WITH NO DATA"]
```

## drop-table, drop-extension, drop-view, drop-materialized-view

`:drop-table` et al can accept a single table (extension, view) name or a sequence of
table (extension, view) names. If a sequence is provided and the first element
is `:if-exists` (or the symbol `if-exists`) then that conditional
clause is added before the table (extension, view) names:

```clojure
user=> (sql/format '{drop-table (if-exists foo bar)})
["DROP TABLE IF EXISTS foo, bar"]
user=> (sql/format {:drop-table [:foo :bar]})
["DROP TABLE foo, bar"]
```

# SQL Pseudo-Syntax Clauses

The following data DSL clauses are supported to let
you modify how SQL clauses are generated, if the default
generation is incorrect or unsupported.

See also the [Extending HoneySQL](extending-honeysql.md) section.

## nest

This is pseudo-syntax that lets you wrap a substatement
in an extra level of parentheses. It should rarely be
needed and it is mostly present to provide the same
functionality for clauses that `[:nest ..]` provides
for expressions.

## raw

This is pseudo-syntax that lets you insert a complete
SQL clause as a string, if HoneySQL doesn't support
some exotic SQL construct. It should rarely be
needed and it is mostly present to provide the same
functionality for clauses that `[:raw ..]` provides
for expressions (which usage is likely to be more common).

# SQL Clauses

HoneySQL supports the following SQL clauses as a data DSL.
These are listed in precedence order (i.e., matching the
order they would appear in a valid SQL statement).

## with, with-recursive

These provide CTE support for several databases.
In the most common form, the argument to
`:with` (or `:with-recursive`) is a sequences of pairs, each of
a result set name (or description) and either of; a basic SQL
statement, a string, a keyword or a symbol.
The result set can either be a SQL entity (a simple name)
or a pair of a SQL entity and a set of column names.

```clojure
user=> (sql/format '{with ((stuff {select (:*) from (foo)}),
                           (nonsense {select (:*) from (bar)}))
                     select (foo.id,bar.name)
                     from (stuff, nonsense)
                     where (= status 0)})
["WITH stuff AS (SELECT * FROM foo), nonsense AS (SELECT * FROM bar) SELECT foo.id, bar.name FROM stuff, nonsense WHERE status = ?" 0]
```

When the expression is a basic SQL statement in any of the pairs,
the resulting syntax of the pair is `WITH ident AS expr` as shown above.
However, when the expression is a string, a keyword or a symbol, the resulting
syntax of the pair is of the form `WITH expr AS ident` like this:

```clojure
user=> (sql/format '{with ((ts_upper_bound "2019-08-01 15:23:00"))
                     select :*
                     from (hits)
                     where (= EventDate ts_upper_bound)})
["WITH ? AS ts_upper_bound SELECT * FROM hits WHERE EventDate = ts_upper_bound" "2019-08-01 15:23:00"]
```

The syntax only varies for each pair and so you can use both SQL statements
and keywords/strings/symbols in the same `WITH` clause like this:

```clojure
user=> (sql/format '{with   ((ts_upper_bound "2019-08-01 15:23:00")
                             (review :awesome)
                             (stuff {select (:*) from (songs)}))
                     select :*
                     from   (hits, stuff)
                     where  (and (= EventDate ts_upper_bound)
                                 (= EventReview review))})
["WITH ? AS ts_upper_bound, awesome AS review, stuff AS (SELECT * FROM songs) SELECT * FROM hits, stuff WHERE (EventDate = ts_upper_bound) AND (EventReview = review)"
 "2019-08-01 15:23:00"]
```

You can specify a list of columns for the CTE like this:

```clojure
user=> (sql/format {:with [[[:stuff {:columns [:id :name]}]
                            {:select [:*] :from [:foo]}]]
                    :select [:id :name]
                    :from [:stuff]
                    :where [:= :status 0]})
["WITH stuff (id, name) AS (SELECT * FROM foo) SELECT id, name FROM stuff WHERE status = ?" 0]
```

You can use a `VALUES` clause in the CTE:

```clojure
user=> (sql/format {:with [[[:stuff {:columns [:id :name]}]
                            {:values [[1 "Sean"] [2 "Jay"]]}]]
                    :select [:id :name]
                    :from [:stuff]})
["WITH stuff (id, name) AS (VALUES (?, ?), (?, ?)) SELECT id, name FROM stuff" 1 "Sean" 2 "Jay"]
```

`:with-recursive` follows the same rules as `:with` and produces `WITH RECURSIVE` instead of just `WITH`.

## intersect, union, union-all, except, except-all

These all expect a sequence of SQL clauses, those clauses
will be wrapped in parentheses, and the SQL keyword interspersed
between those clauses.

```clojure
user=> (sql/format '{union [{select (id,status) from (table-a)}
                            {select (id,(event status) from (table-b))}]})
["SELECT id, status FROM table_a UNION SELECT id, event AS status, from, table_b"]
```

> Note: different databases have different precedence rules for these set operations when used in combination -- you may need to use `:nest` to add `(` .. `)` in order to combine these operations in a single SQL statement, if the natural order produced by HoneySQL does not work "as expected" for your database.

## select, select-distinct, table

`:select` and `:select-distinct` expect a sequence of SQL entities (column names
or expressions). Any of the SQL entities can be a pair of entity and alias. If you are selecting an expression, you would most
often provide an alias for the expression, but it can be omitted
as in the following:

```clojure
user=> (sql/format '{select (id, ((* cost 2)), (event status))
                     from (table)})
["SELECT id, cost * ?, event AS status FROM table" 2]
```

Here, `:select` has a three expressions as its argument. The first is
a simple column name. The second is an expression with no alias, which
is why it is still double-nested. The third is a simple column name and its alias.

With an alias on the expression:

```clojure
user=> (sql/format {:select [:id, [[:* :cost 2] :total], [:event :status]]
                    :from [:table]})
["SELECT id, cost * ? AS total, event AS status FROM table" 2]
```

Here, `:select` has a three expressions as its argument. The first is
a simple column name. The second is an expression and its alias. The
third is a simple column name and its alias.

`:select-distinct` works the same way but produces `SELECT DISTINCT`.

> Google BigQuery support: to provide `SELECT * EXCEPT ..` and `SELECT * REPLACE ..` syntax, HoneySQL supports a vector starting with `:*` or the symbol `*` followed by except columns and/or replace expressions as columns:

```clojure
user=> (sql/format {:select [[:* :except [:a :b :c]]] :from [:table]})
["SELECT * EXCEPT (a, b, c) FROM table"]
user=> (sql/format {:select [[:* :replace [[[:* :a [:inline 100]] :b] [[:inline 2] :c]]]] :from [:table]})
["SELECT * REPLACE (a * 100 AS b, 2 AS c) FROM table"]
user=> (sql/format {:select [[:* :except [:a :b] :replace [[[:inline 2] :c]]]] :from [:table]})
["SELECT * EXCEPT (a, b) REPLACE (2 AS c) FROM table"]
```

The `:table` clause is equivalent to `:select :* :from` and accepts just
a simple table name -- see `:create-table-as` above for an example.

## select-distinct-on

Similar to `:select-distinct` above but the first element
in the sequence should be a sequence of columns for the
`DISTINCT ON` clause and the remaining elements are the
columns to be selected:

```clojure
user=> (sql/format '{select-distinct-on [[a b] c d]
                     from [table]})
["SELECT DISTINCT ON(a, b) c, d FROM table"]
```

## select-top, select-distinct-top

`:select-top` and `:select-distinct-top` are variants of `:select`
and `:select-distinct`, respectively, that provide support for
MS SQL Server's `TOP` modifier on a `SELECT` statement.

They accept a sequence that starts with an expression to be
used as the `TOP` limit value, followed by SQL entities as
supported by `:select` above.

The `TOP` expression can either be a general SQL expression
or a sequence whose first element is a general SQL expression,
followed by qualifiers for `:percent` and/or `:with-ties` (or
the symbols `percent` and/or `with-ties`).

```clojure
user=> (sql/format {:select-top [[10 :percent :with-ties] :foo :baz] :from :bar :order-by [:quux]})
["SELECT TOP(?) PERCENT WITH TIES foo, baz FROM bar ORDER BY quux ASC" 10]
```
## into

Used for selecting rows into a new table, optional in another database:

```clojure
user=> (sql/format '{select * into newtable from mytable})
["SELECT * INTO newtable FROM mytable"]
user=> (sql/format '{select * into [newtable otherdb] from mytable})
["SELECT * INTO newtable IN otherdb FROM mytable"]
```

## bulk-collect-into

Used for selecting rows into an array variable, with an optional limit:

```clojure
user=> (sql/format '{select * bulk-collect-into arrv from mytable})
["SELECT * BULK COLLECT INTO arrv FROM mytable"]
user=> (sql/format '{select * bulk-collect-into [arrv 100] from mytable})
["SELECT * BULK COLLECT INTO arrv LIMIT ? FROM mytable" 100]
```

## insert-into, replace-into

There are three use cases with `:insert-into`.

The first case takes just a table specifier (either a
table name or a table/alias pair),
and then you can optionally specify the columns (via a `:columns` clause).

The second case takes a pair of a table specifier (either a
table name or table/alias pair) and a sequence of column
names (so you do not need to also use `:columns`).

The third case takes a pair of either a table specifier
or a table/column specifier and a SQL query.

For the first and second cases, you'll use the `:values` clause
to specify rows of values to insert.

`:replace-into` is only supported by MySQL and SQLite but is
part of HoneySQL's "core" dialect anyway. It produces a `REPLACE INTO`
statement but otherwise has identical syntax to `:insert-into`.

```clojure
;; first case -- table specifier:
user=> (sql/format {:insert-into :transport
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
user=> (sql/format {:insert-into :transport
                    :columns [:id :name]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport (id, name) VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
;; with an alias:
user=> (sql/format {:insert-into [:transport :t]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport AS t VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
user=> (sql/format {:insert-into [:transport :t]
                    :columns [:id :name]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport AS t (id, name) VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
;; second case -- table specifier and columns:
user=> (sql/format {:insert-into [:transport [:id :name]]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport (id, name) VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
;; with an alias:
user=> (sql/format {:insert-into [[:transport :t] [:id :name]]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport AS t (id, name) VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
;; third case -- table/column specifier and query:
user=> (sql/format '{insert-into (transport {select (id, name) from (cars)})})
["INSERT INTO transport SELECT id, name FROM cars"]
;; with columns:
user=> (sql/format '{insert-into ((transport (id, name)) {select (*) from (cars)})})
["INSERT INTO transport (id, name) SELECT * FROM cars"]
;; with an alias:
user=> (sql/format '{insert-into ((transport t) {select (id, name) from (cars)})})
["INSERT INTO transport AS t SELECT id, name FROM cars"]
;; with an alias and columns:
user=> (sql/format '{insert-into (((transport t) (id, name)) {select (*) from (cars)})})
["INSERT INTO transport AS t (id, name) SELECT * FROM cars"]
```

> Note: if you specify `:columns` for an `:insert-into` that also includes column names, you will get invalid SQL. Similarly, if you specify `:columns` when `:values` is based on hash maps, you will get invalid SQL. Since clauses are generated independently, there is no cross-checking performed if you provide an illegal combination of clauses.

## update

`:update` expects either a simple SQL entity (table name)
or a pair of the table name and an alias:

```clojure
user=> (sql/format {:update :transport
                    :set {:name "Yacht"}
                    :where [:= :id 2]})
["UPDATE transport SET name = ? WHERE id = ?" "Yacht" 2]
```

You can also set columns to `NULL` or to their default values:

```clojure
user=> (sql/format {:update :transport
                    :set {:owner nil, :date_built [:default]}
                    :where [:= :id 2]})
["UPDATE transport SET owner = NULL, date_built = DEFAULT WHERE id = ?"
 2]
```

## delete, delete-from

`:delete-from` is the simple use case here, accepting just a
SQL entity (table name). `:delete` allows for deleting from
multiple tables, accepting a sequence of either table names
or aliases:

```clojure
user=> (sql/format '{delete-from transport where (= id 1)})
["DELETE FROM transport WHERE id = ?" 1]
user=> (sql/format {:delete [:order :item]
                    :from [:order]
                    :join [:item [:= :order.item-id :item.id]]
                    :where [:= :item.id 42]})
["DELETE order, item FROM order INNER JOIN item ON order.item_id = item.id WHERE item.id = ?" 42]
```

## truncate

`:truncate` accepts a simple SQL entity (table name)
or a table name followed by various options:

```clojure
user=> (sql/format '{truncate transport})
["TRUNCATE TABLE transport"]
user=> (sql/format '{truncate (transport restart identity)})
["TRUNCATE TABLE transport RESTART IDENTITY"]
```

## columns

Wherever you need just a list of column names `:columns`
accepts a sequence of SQL entities (names). We saw an
example above with `:insert-into`.

## set (ANSI)

`:set` accepts a hash map of SQL entities and the values
that they should be assigned. This precedence -- between
`:columns` and `:from` -- corresponds to ANSI SQL which
is correct for most databases. The MySQL dialect that
HoneySQL 2.x supports has a different precedence (below).

```clojure
user=> (sql/format {:update :order
                    :set {:line-count [:+ :line-count 1]}
                    :where [:= :item-id 42]})
["UPDATE order SET line_count = line_count + ? WHERE item_id = ?" 1 42]
```

## from

`:from` accepts a single sequence argument that lists
one or more SQL entities. Each entity can either be a
simple table name (keyword or symbol) or a pair of a
table name and an alias:

```clojure
user=> (sql/format {:select [:username :name]
                    :from [:user :status]
                    :where [:and [:= :user.statusid :status.id]
                                 [:= :user.id 9]]})
["SELECT username, name FROM user, status WHERE (user.statusid = status.id) AND (user.id = ?)" 9]
user=> (sql/format {:select [:u.username :s.name]
                    :from [[:user :u] [:status :s]]
                    :where [:and [:= :u.statusid :s.id]
                                 [:= :u.id 9]]})
["SELECT u.username, s.name FROM user AS u, status AS s WHERE (u.statusid = s.id) AND (u.id = ?)" 9]
```

> Note: the actual formatting of a `:from` clause is currently identical to the formatting of a `:select` clause.

## using

`:using` accepts a single sequence argument that lists
one or more SQL entities. Each entity can either be a
simple table name (keyword or symbol) or a pair of a
table name and an alias.

`:using` is intended to be used as a simple join, for example with a `:delete-from`
clause (see [PostgreSQL DELETE statement](https://www.postgresql.org/docs/12/sql-delete.html)
for more detail).

> Note: the actual formatting of a `:using` clause is currently identical to the formatting of a `:select` clause.

## join-by

This is a convenience that allows for an arbitrary sequence of `JOIN`
operations to be performed in a specific order. It accepts either a sequence
of alternating join operation name (keyword or symbol) and the clause that join
would take, or a sequence of `JOIN` clauses as hash maps:

```clojure
user=> (sql/format {:select [:t.ref :pp.code]
                    :from [[:transaction :t]]
                    :join-by [:left [[:paypal-tx :pp]
                                     [:using :id]]
                              :join [[:logtransaction :log]
                                     [:= :t.id :log.id]]]
                    :where [:= "settled" :pp.status]}
                    {:pretty true})
["
SELECT t.ref, pp.code
FROM transaction AS t
LEFT JOIN paypal_tx AS pp USING (id) INNER JOIN logtransaction AS log ON t.id = log.id
WHERE ? = pp.status
" "settled"]

;; or the equivalent using helpers:
user=> (sql/format (-> (select :t.ref :pp.code)
                       (from [:transaction :t])
                       (join-by (left-join [:paypal-tx :pp]
                                           [:using :id])
                                (join [:logtransaction :log]
                                      [:= :t.id :log.id]))
                       (where := "settled" :pp.status))
                   {:pretty true})
["
SELECT t.ref, pp.code
FROM transaction AS t
LEFT JOIN paypal_tx AS pp USING (id) INNER JOIN logtransaction AS log ON t.id = log.id
WHERE ? = pp.status
" "settled"]
```

Without `:join-by`, a `:join` would normally be generated before a `:left-join`.
To avoid repetition, `:join-by` allows shorthand versions of the join clauses
using a keyword (or symbol) without the `-join` suffix, as shown in this example.

## join, left-join, right-join, inner-join, outer-join, full-join

All these join clauses have the same structure: they accept a sequence
of alternating SQL entities (table names) and conditions that specify
how to perform the join. The table names can either be simple names
or a pair of a table name and an alias:

```clojure
user=> (sql/format {:select [:u.username :s.name]
                    :from [[:user :u]]
                    :join [[:status :s] [:= :u.statusid :s.id]]
                    :where [:= :s.id 2]})
["SELECT u.username, s.name FROM user AS u INNER JOIN status AS s ON u.statusid = s.id WHERE s.id = ?" 2]
```

`:join` is shorthand for `:inner-join`.

An alternative to a join condition is a `USING` expression:

```clojure
user=> (sql/format {:select [:t.ref :pp.code]
                    :from [[:transaction :t]]
                    :left-join [[:paypal-tx :pp]
                                [:using :id]]
                    :where [:= "settled" :pp.status]})
["SELECT t.ref, pp.code FROM transaction AS t LEFT JOIN paypal_tx AS pp USING (id) WHERE ? = pp.status" "settled"]
```

See also the [`:join` special syntax](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started/sql-special-syntax-#join)
for nested `JOIN` expressions.

## cross-join

`:cross-join` accepts a single sequence argument that lists
one or more SQL expressions. Each expression can either be a
simple table name (keyword or symbol) or a pair of a
table expression and an alias.

```clojure
user=> (sql/format {:select [:foo.id [:x.id :x_id] :x.value]
                    :cross-join [[[:lateral
                                   [:jsonb_to_recordset :foo.json_value]]
                                  [[:raw "x(id text, value jsonb)"]]]]
                    :from [:foo]})
["SELECT foo.id, x.id AS x_id, x.value FROM foo CROSS JOIN LATERAL JSONB_TO_RECORDSET(foo.json_value) x(id text, value jsonb)"]
```

Here, `:cross-join` has a one expression as its argument, which is a
table expression and an alias. The table expression is `[:lateral ..]`
and the alias expression is double-nested so that it is read as a
function call: an invocation of `:raw`.

> Note: the actual formatting of a `:cross-join` clause is currently identical to the formatting of a `:select` clause.

## set (MySQL)

This is the precedence of the `:set` clause for the MySQL dialect.
It is otherwise identical to the `:set` clause described above.

## where

The `:where` clause can have a single SQL expression, or
a sequence of SQL expressions prefixed by either `:and`
or `:or`. See examples of `:where` in various clauses above.

Sometimes it is convenient to construct a `WHERE` clause that
tests several columns for equality, and you might have a Clojure
hash map containing those values. `honey.sql/map=` exists to
convert a hash map of values into a condition that you can use
in a `WHERE` clause to match against those columns and values:

```clojure
user=> (sql/format {:select :* :from :transaction :where (sql/map= {:type "sale" :productid 123})})
["SELECT * FROM transaction WHERE (type = ?) AND (productid = ?)" "sale" 123]
```

## group-by

`:group-by` accepts a sequence of one or more SQL expressions.

```clojure
user=> (sql/format '{select (*) from (table)
                     group-by (status, (year created-date))})
["SELECT * FROM table GROUP BY status, YEAR(created_date)"]
```

## having

The `:having` clause works identically to `:where` above
but is rendered into the SQL later in precedence order.

## window, partition-by (and over)

`:window` accepts a pair of SQL entity (the window name)
and the window "function" as a SQL clause (a hash map).

`:partition-by` accepts the same arguments as `:select` above
(even though the allowable SQL generated is much more restrictive).

These are expected to be used with the `:over` expression (special syntax).

```clojure
user=> (sql/format {:select [:id
                             [[:over
                               [[:avg :salary]
                                {:partition-by [:department]
                                 :order-by [:designation]}
                                :Average]
                               [[:max :salary]
                                :w
                                :MaxSalary]]]]
                    :from [:employee]
                    :window [:w {:partition-by [:department]}]}
                    {:pretty true})
["
SELECT id, AVG(salary) OVER (PARTITION BY department ORDER BY designation ASC) AS Average, MAX(salary) OVER w AS MaxSalary
FROM employee
WINDOW w AS (PARTITION BY department)
"]
;; easier to write with helpers (and easier to read!):
user=> (sql/format (-> (select :id
                               (over [[:avg :salary] (-> (partition-by :department) (order-by :designation)) :Average]
                                     [[:max :salary] :w :MaxSalary]))
                       (from :employee)
                       (window :w (partition-by :department)))
                   {:pretty true})
["
SELECT id, AVG(salary) OVER (PARTITION BY department ORDER BY designation ASC) AS Average, MAX(salary) OVER w AS MaxSalary
FROM employee
WINDOW w AS (PARTITION BY department)
"]
```

The window function in the `:over` expression may be `{}` or `nil`:

```clojure
user=> (sql/format {:select [:id
                             [[:over
                               [[:avg :salary]
                                {}
                                :Average]
                               [[:max :salary]
                                nil
                                :MaxSalary]]]]
                    :from [:employee]})
["SELECT id, AVG(salary) OVER () AS Average, MAX(salary) OVER () AS MaxSalary FROM employee"]
;; easier to write with helpers (and easier to read!):
user=> (sql/format (-> (select :id
                               (over [[:avg :salary] {} :Average]
                                     [[:max :salary] nil :MaxSalary]))
                       (from :employee)))
["SELECT id, AVG(salary) OVER () AS Average, MAX(salary) OVER () AS MaxSalary FROM employee"]
```

## order-by

`:order-by` accepts a sequence of one or more ordering
expressions. Each ordering expression is either a simple
SQL entity or a pair of a SQL expression and a direction
(which can be `:asc`, `:desc`, `:nulls-first`, `:desc-null-last`,
etc -- or the symbol equivalent).

If you want to order by an expression, you should wrap it
as a pair with a direction:

```clojure
user=> (sql/format '{select (*) from table
                     ;; simple orderings:
                     order-by (status, created-date)})
["SELECT * FROM table ORDER BY status ASC, created_date ASC"]
user=> (sql/format '{select (*) from table
                     ;; explicit direction provided:
                     order-by ((status asc), ((year created-date) asc))})
["SELECT * FROM table ORDER BY status ASC, YEAR(created_date) ASC"]
```

The default direction is ascending and if you provide a wrapped
expression you _can_ omit the direction if you want:

```clojure
user=> (sql/format {:select [:*] :from :table
                    ;; expression without direction is still wrapped:
                    :order-by [:status, [[:year :created-date]]]})
["SELECT * FROM table ORDER BY status ASC, YEAR(created_date) ASC"]
;; a more complex order by with case (and direction):
user=> (sql/format {:select [:*] :from :table
                    :order-by [[[:case [:< [:now] :expiry-date]
                                 :created-date :else :expiry-date]
                                :desc]]})
["SELECT * FROM table ORDER BY CASE WHEN NOW() < expiry_date THEN created_date ELSE expiry_date END DESC"]
```

## limit, offset, fetch

Some databases, including MySQL, support `:limit` and `:offset`
for paginated queries, other databases support `:offset` and
`:fetch` for that (which is ANSI-compliant and should be
preferred if your database supports it). All three expect a
single SQL expression:

```clojure
user=> (sql/format {:select [:id :name]
                    :from [:table]
                    :limit 10 :offset 20})
["SELECT id, name FROM table LIMIT ? OFFSET ?" 10 20]
user=> (sql/format {:select [:id :name]
                    :from [:table]
                    :offset 20 :fetch 10})
["SELECT id, name FROM table OFFSET ? ROWS FETCH NEXT ? ROWS ONLY" 20 10]
```

All three are available in all dialects for HoneySQL so it
is up to you to choose the correct pair for your database.

If you use `:offset` and `:limit` together, `OFFSET` will just have
the number of rows. If you use `:offset` and `:fetch` together,
`OFFSET` will have the number of rows and the `ROWS` keyword. If
you use `:offset` on its own, it will have just the number
of rows, unless you have the `:sqlserver` dialect selected,
it which case it will have the `ROWS` keywords as well.
_This seemed to be the least risky change in 2.0.0 RC 5 to avoid introducing a breaking change._

If the number of rows is one, `ROW` will be used instead of `ROWS`.
If `:fetch` is specified without `:offset`, `FIRST` will be used instead of `NEXT`.

## for

The `:for` clause accepts either a single item -- the lock
strength -- or a sequence of up to three items of which the
first is the lock strength, followed by an optional table
name (or sequence of table names), followed by how to deal
with the lock:

```clojure
user=> (sql/format '{select (*) from (table)
                     for update})
["SELECT * FROM table FOR UPDATE"]
user=> (sql/format '{select (*) from (table)
                     for no-key-update})
["SELECT * FROM table FOR NO KEY UPDATE"]
user=> (sql/format '{select (*) from (table)
                     for (key-share wait)})
["SELECT * FROM table FOR KEY SHARE WAIT"]
user=> (sql/format '{select (*) from (table)
                     for (update bar wait)})
["SELECT * FROM table FOR UPDATE OF bar WAIT"]
user=> (sql/format '{select (*) from (table)
                     for (update (bar quux) wait)})
["SELECT * FROM table FOR UPDATE OF bar, quux WAIT"]
```

The lock strength can be any SQL keyword or phrase
represented as a Clojure keyword (or symbol), with
spaces represented by `-`.

The three SQL keywords/phrases that are recognized
as not being a table name in the second slot are
`NOWAIT`, `SKIP LOCKED`, and `WAIT`.

However, in the case where a table name (or sequence
of table names) is present, no check is made on the
keyword or phrase in that third slot (although it is
expected to be just one of those three mentioned above).

## lock (MySQL)

The syntax accepted for MySQL's `:lock` is exactly the
same as the `:for` clause above.

## values

`:values` accepts either a sequence of hash maps representing
row values or a sequence of sequences, also representing row
values.

In the former case, all of the rows are augmented to have
either `NULL` or `DEFAULT` values for any missing keys (columns).
By default, `NULL` is used but you can specify a set of columns
to get `DEFAULT` values, via the `:values-default-columns` option.
You can also be explicit and use `[:default]` as a value to generate `DEFAULT`.
In the latter case -- a sequence of sequences --
all of the rows are padded to the same length by adding `nil`
values if needed (since `:values` does not know how or if column
names are being used in this case).

```clojure
user=> (sql/format {:insert-into :table
                    :values [[1 2] [2 3 4 5] [3 4 5]]})
["INSERT INTO table VALUES (?, ?, NULL, NULL), (?, ?, ?, ?), (?, ?, ?, NULL)" 1 2 2 3 4 5 3 4 5]
user=> (sql/format '{insert-into table
                     values ({id 1 name "Sean"}
                             {id 2}
                             {name "Extra"})})
["INSERT INTO table (id, name) VALUES (?, ?), (?, NULL), (NULL, ?)" 1 "Sean" 2 "Extra"]
user=> (sql/format '{insert-into table
                     values ({id 1 name "Sean"}
                             {id 2}
                             {name "Extra"})}
                   {:values-default-columns #{'id}})
["INSERT INTO table (id, name) VALUES (?, ?), (?, NULL), (DEFAULT, ?)" 1 "Sean" 2 "Extra"]
```

> Note: the `:values-default-columns` option must match how the columns are specified, i.e., as symbols or keywords.

For databases that allow it, you can insert an entire row of default values,
if appropriate, using one of the following syntaxes:

```clojure
user=> (sql/format {:insert-into :table :values []})
["INSERT INTO table VALUES ()"]
user=> (sql/format {:insert-into :table :values :default})
["INSERT INTO table DEFAULT VALUES"]
```

Some databases support the empty `VALUES` clause, some support `DEFAULT VALUES`, some support neither. Consult your database's documentation to see which approach to use.

For databases that allow it, when specifying multiple rows you use `:default` in
place of a row to insert default values for that row:

```clojure
user=> (sql/format {:insert-into :table
                    :values [{:a 1 :b 2 :c 3}
                             :default
                             {:a 4 :b 5 :c 6}]})
["INSERT INTO table (a, b, c) VALUES (?, ?, ?), DEFAULT, (?, ?, ?)" 6 5 4]
user=> (sql/format {:insert-into :table
                    :values [[1 2 3] :default [4 5 6]]})
["INSERT INTO table VALUES (?, ?, ?), DEFAULT, (?, ?, ?)" 1 2 3 4 5 6]
```

## on-conflict, on-constraint, do-nothing, do-update-set

These are grouped together because they are handled
as if they are separate clauses but they will appear
in pairs: `ON ... DO ...`.

`:on-conflict` accepts a sequence of zero or more
SQL entities (keywords or symbols), optionally
followed by a single SQL clause (hash map). It can also
accept either a single SQL entity or a single SQL clause.
The SQL entities are column names and the SQL clause can be an
`:on-constraint` clause or a`:where` clause.

_[For convenience of use with the `on-conflict` helper, this clause can also accept any of those arguments, wrapped in a sequence; it can also accept an empty sequence, and just produce `ON CONFLICT`, so that it can be combined with other clauses directly]_

`:on-constraint` accepts a single SQL entity that
identifies a constraint name.

Since `:do-nothing` is a SQL clause but has no
associated data, it still has to have an arbitrary
value because clauses are hash maps and that value
will be ignored so `:do-nothing true` is a
reasonable choices.

`:do-update-set` accepts either a single SQL entity
(a keyword or symbol), or hash map of columns and
values, like `:set` (above), or a hash map of fields
(a sequence of SQL entities) and a where clause.
For convenience of building clauses with helpers,
it also accepts a sequence of one or more column
names followed by an optional hash map: this is treated
as an alternative form of the hash map with fields
and a where clause.
The single SQL entity and the list of fields produce
`SET` clauses using `EXCLUDED`:

```clojure
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict :name
                    :do-update-set :name})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name" "Microsoft"]
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict :name
                    :do-update-set {:name [:|| "was: " :EXCLUDED.name]}})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = ? || EXCLUDED.name" "Microsoft" "was: "]
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict :name
                    :do-update-set {:fields [:name]
                                    :where [:<> :name nil]}})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name WHERE name IS NOT NULL" "Microsoft"]
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict :name
                    :do-update-set {:fields {:name [:+ :table.name 1]}
                                    :where [:<> :name nil]}})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = table.name + ? WHERE name IS NOT NULL" "Microsoft" 1]
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict {:on-constraint :name-idx}
                    :do-nothing true})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT ON CONSTRAINT name_idx DO NOTHING" "Microsoft"]
;; empty :on-conflict combined with :on-constraint clause:
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict []
                    :on-constraint :name-idx
                    :do-nothing true})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT ON CONSTRAINT name_idx DO NOTHING" "Microsoft"]
```

## on-duplicate-key-update

This is the MySQL equivalent of `on-update-set` described above.

## returning

`:returning` accepts a single sequence argument that lists
one or more SQL entities. Each entity can either be a
simple table name (keyword or symbol) or a pair of a
table name and an alias.

> Note: the actual formatting of a `:returning` clause is currently identical to the formatting of a `:select` clause.

## with-data

`:with-data` accepts a single boolean argument and produces
either `WITH DATA`, for a `true` argument, or `WITH NO DATA`,
for a `false` argument.
