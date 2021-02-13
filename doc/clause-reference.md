# SQL Clauses Supported

This section lists all the SQL clauses that HoneySQL
supports out of the box, in the order that they are
processed for formatting.

Clauses can be specified as keywords or symbols. Use
`-` in the clause name where the formatted SQL would have
a space (e.g., `:left-join` is formatted as `LEFT JOIN`).

Except as noted, these clauses apply to all the SQL
dialects that HoneySQL supports.

## alter-table, add-column, drop-column, modify-column, rename-column

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
                    :drop-column :ident})
["ALTER TABLE fruit DROP COLUMN ident"]
user=> (sql/format {:alter-table :fruit
                    :modify-column [:id :int :unsigned nil]})
["ALTER TABLE fruit MODIFY COLUMN id INT UNSIGNED NULL"]
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
```

As can be seen above, `:add-column` and `:modify-column`
both accept a column description (as a sequence of simple
expressions); `:drop-column` accepts a single column name,
and `:rename-column` accepts a sequence with two column
names: the "from" and the "to" names.

## add-index, drop-index

`:add-index` accepts a single (function) expression
that describes an index, and `:drop-index` accepts a
single index name:

```clojure
user=> (sql/format {:alter-table :fruit
                    :add-index [:index :look :appearance]})
["ALTER TABLE fruit ADD INDEX look(appearance)"]
user=> (sql/format {:alter-table :fruit
                    :add-index [:unique nil :color :appearance]})
["ALTER TABLE fruit ADD UNIQUE(color,appearance)"]
user=> (sql/format {:alter-table :fruit :drop-index :look})
["ALTER TABLE fruit DROP INDEX look"]
```

## create-table, with-columns

`:create-table` can accept a single table name or a pair
containing a table name and a flag indicating the creation
should be conditional (`:if-not-exists` or the symbol `if-not-exists`,
although any truthy value will work). `:create-table` should
be used with `:with-columns` to specify the actual columns
in the table:

```clojure
user=> (sql/format {:create-table :fruit
                    :with-columns
                    [[:id :int [:not nil]]
                     [:name [:varchar 32] [:not nil]]
                     [:cost :float :null]]})
;; reformatted for clarity:
["CREATE TABLE fruit (
  id INT NOT NULL,
  name VARCHAR(32) NOT NULL,
  cost FLOAT NULL
)"]
```

The `:with-columns` clause is formatted as if `{:inline true}`
was specified so nothing is parameterized. In addition,
everything except the first element of a column description
will be uppercased (mostly to give the appearance of separating
the column name from the SQL keywords).

Various function-like expressions can be specified, as shown
in the example above, but allow things like `CHECK` for a
constraint, `FOREIGN KEY` (with a column name), `REFERENCES`
(with a pair of column names). See [special-syntax.md#clause-descriptors](Clause Descriptors in Special Syntax) for more details.

## create-view

`:create-view` accepts a single view name:

```clojure
user=> (sql/format {:create-view :products
                    :select [:*]
                    :from [:items]
                    :where [:= :category "product"]})
["CREATE VIEW products AS SELECT * FROM items WHERE category = ?" "product"]
```

## drop-table

`:drop-table` can accept a single table name or a sequence of
table names. If a sequence is provided and the first element
is `:if-exists` (or the symbol `if-exists`) then that conditional
clause is added before the table names:

```clojure
user=> (sql/format '{drop-table (if-exists foo bar)})
["DROP TABLE IF EXISTS foo, bar"]
user=> (sql/format {:drop-table [:foo :bar]})
["DROP TABLE foo, bar"]
```

## rename-table

`:rename-table` accepts a pair of the "from" table name
and the "to" table names:

```clojure
user=> (sql/format {:rename-table [:fruit :vegetable]})
["RENAME TABLE fruit TO vegetable"]
```

## nest

This is pseudo-syntax that lets you wrap a substatement
in an extra level of parentheses. It should rarely be
needed and it is mostly present to provide the same
functionality for clauses that `[:nest ..]` provides
for expressions.

## with, with-recursive

These provide CTE support for SQL Server. The argument to
`:with` (or `:with-recursive`) is a sequences of pairs, each of
a result set name (or description) and a basic SQL statement.
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
["(SELECT id, status FROM table_a) UNION (SELECT id, event AS status, from, table_b)"]
```

## select, select-distinct

`:select` expects a sequence of SQL entities (column names
or expressions). Any of the SQL entities can be a pair of entity and alias. If you are selecting an expression, you would most
often provide an alias for the expression, but it can be omitted
as in the following:

```clojure
user=> (sql/format '{select (id, ((* cost 2)), (event status))
                     from (table)})
["SELECT id, cost * ?, event AS status FROM table" 2]
```

With an alias on the expression:

```clojure
user=> (sql/format {:select [:id, [[:* :cost 2] :total], [:event :status]]
                    :from [:table]})
["SELECT id, cost * ? AS total, event AS status FROM table" 2]
```

`:select-distinct` works the same way but produces `SELECT DISTINCT`.

HoneySQL does not yet support `SELECT .. INTO ..`
or `SELECT .. BULK COLLECT INTO ..`.

## insert-into

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
;; with columns:
user=> (sql/format '{insert-into ((transport (id, name)) {select (*) from (cars)})})
["INSERT INTO transport (id, name) SELECT * FROM cars"]
;; with an alias and columns:
user=> (sql/format '{insert-into (((transport t) (id, name)) {select (*) from (cars)})})
["INSERT INTO transport AS t (id, name) SELECT * FROM cars"]
```

## update

`:update` expects either a simple SQL entity (table name)
or a pair of the table name and an alias:

```clojure
user=> (sql/format {:update :transport
                    :set {:name "Yacht"}
                    :where [:= :id 2]})
["UPDATE transport SET name = ? WHERE id = ?" "Yacht" 2]
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

`:truncate` accepts a simple SQL entity (table name):

```clojure
user=> (sql/format '{truncate transport})
["TRUNCATE transport"]
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
HoneySQL 2.0 supports has a different precedence (below).

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

`:using` is intended to be used as a simple join with a `:delete-from`
clause (see [PostgreSQL DELETE statement](https://www.postgresql.org/docs/12/sql-delete.html)
for more detail).

> Note: the actual formatting of a `:using` clause is currently identical to the formatting of a `:select` clause.

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

An alternative to a join condition is a `USING` expression:

```clojure
user=> (sql/format {:select [:t.ref :pp.code]
                    :from [[:transaction :t]]
                    :left-join [[:paypal-tx :pp]
                                [:using :id]]
                    :where [:= "settled" :pp.status]})
["SELECT t.ref, pp.code FROM transaction AS t LEFT JOIN paypal_tx AS pp USING (id) WHERE ? = pp.status" "settled"]
```

## cross-join

`:cross-join` accepts a single sequence argument that lists
one or more SQL entities. Each entity can either be a
simple table name (keyword or symbol) or a pair of a
table name and an alias.

> Note: the actual formatting of a `:cross-join` clause is currently identical to the formatting of a `:select` clause.

## set (MySQL)

This is the precedence of the `:set` clause for the MySQL dialect.
It is otherwise identical to the `:set` clause described above.

## where

The `:where` clause can have a single SQL expression, or
a sequence of SQL expressions prefixed by either `:and`
or `:or`. See examples of `:where` in various clauses above.

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
                    :window [:w {:partition-by [:department]}]})
["SELECT id, AVG(salary) OVER (PARTITION BY department ORDER BY designation ASC) AS Average, MAX(salary) OVER w AS MaxSalary FROM employee WINDOW w AS (PARTITION BY department)"]
;; easier to write with helpers (and easier to read!):
user=> (sql/format (-> (select :id
                               (over [[:avg :salary] (-> (partition-by :department) (order-by :designation)) :Average]
                                     [[:max :salary] :w :MaxSalary]))
                       (from :employee)
                       (window :w (partition-by :department))))
["SELECT id, AVG(salary) OVER (PARTITION BY department ORDER BY designation ASC) AS Average, MAX(salary) OVER w AS MaxSalary FROM employee WINDOW w AS (PARTITION BY department)"]
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
(which can be `:asc` or `:desc` -- or the symbol equivalent).

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
```

## limit, offset (MySQL)

Both `:limit` and `:offset` expect a single SQL expression:

```clojure
user=> (sql/format {:select [:id :name]
                    :from [:table]
                    :limit 20 :offset 20})
["SELECT id, name FROM table LIMIT ? OFFSET ?" 20 20]
```

> Note: In the prerelease, these MySQL-specific clauses are in the default dialect but these will be moved to the `:mysql` dialect.

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

> Note: In the prerelease, this MySQL-specific clauses is in the default dialect but this will be moved to the `:mysql` dialect.

## values

`:values` accepts either a sequence of hash maps representing
row values or a sequence of sequences, also representing row
values.

In the former case, all of the rows are augmented to have
`nil` values for any missing keys (columns). In the latter,
all of the rows are padded to the same length by adding `nil`
values if needed.

```clojure
user=> (sql/format {:insert-into :table
                    :values [[1 2] [2 3 4 5] [3 4 5]]})
["INSERT INTO table VALUES (?, ?, NULL, NULL), (?, ?, ?, ?), (?, ?, ?, NULL)" 1 2 2 3 4 5 3 4 5]
user=> (sql/format '{insert-into table
                     values ({id 1 name "Sean"}
                             {id 2}
                             {name "Extra"})})
["INSERT INTO table (id, name) VALUES (?, ?), (?, NULL), (NULL, ?)" 1 "Sean" 2 "Extra"]
```

## on-conflict, on-constraint, do-nothing, do-update-set

These are grouped together because they are handled
as if they are separate clauses but they will appear
in pairs: `ON ... DO ...`.

`:on-conflict` accepts either a single SQL entity
(a keyword or symbol) or a SQL clause. That's either
a column name or an `:on-constraint` clause or a
`:where` clause.

`:on-constraint` accepts a single SQL entity that
identifies a constraint name.

Since `:do-nothing` is a SQL clause but has no
associated data, it still has to have an arbitrary
value because clauses are hash maps and that value
will be ignored so `:do-nothing true` is a
reasonable choices.

`:do-update-set` accepts either a single SQL entity
(a keyword or symbol) or hash map of columns and
values, like `:set` (above). The former produces
a `SET` clause using `EXCLUDED`:

```clojure
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict :name
                    :do-update-set :name})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name" "Microsoft"]
user=> (sql/format {:insert-into :companies
                    :values [{:name "Microsoft"}]
                    :on-conflict {:on-constraint :name-idx}
                    :do-nothing true})
["INSERT INTO companies (name) VALUES (?) ON CONFLICT ON CONSTRAINT name_idx DO NOTHING" "Microsoft"]
```

## returning

`:returning` accepts a single sequence argument that lists
one or more SQL entities. Each entity can either be a
simple table name (keyword or symbol) or a pair of a
table name and an alias.

> Note: the actual formatting of a `:returning` clause is currently identical to the formatting of a `:select` clause.
