# SQL Clauses Supported

This section lists all the SQL clauses that HoneySQL
supports out of the box, in the order that they are
processed for formatting.

Clauses can be specified as keywords or symbols. Use
`-` in the clause name where the formatted SQL would have
a space (e.g., `:left-join` is formatted as `LEFT JOIN`).

Except as noted, these clauses apply to all the SQL
dialects that HoneySQL supports.

## nest

This is pseudo-syntax that lets you wrap a substatement
in an extra level of parentheses. It should rarely be
needed and it is mostly present to provide the same
functionality for clauses that `[:nest ..]` provides
for expressions.

## with, with-recursive

These provide CTE support for SQL Server. The argument to
`:with` (or `:with-recursive`) is a pair of
a result set name (or description) and a basic SQL statement.
The result set can either be a SQL entity (a simple name)
or a pair of a SQL entity and a set of column names.

```clojure
user=> (sql/format '{with (stuff {select (:*) from (foo)})
                     select (id,name)
                     from (stuff)
                     where (= status 0)})
["WITH stuff AS (SELECT * FROM foo) SELECT id, name FROM stuff WHERE status = ?" 0]
```

You can specify a list of columns for the CTE like this:

```clojure
user=> (sql/format {:with [[:stuff {:columns [:id :name]}]
                           {:select [:*] :from [:foo]}]
                    :select [:id :name]
                    :from [:stuff]
                    :where [:= :status 0]})
["WITH stuff (id, name) AS (SELECT * FROM foo) SELECT id, name FROM stuff WHERE status = ?" 0]
```

You can use a `VALUES` clause in the CTE:

```clojure
user=> (sql/format {:with [[:stuff {:columns [:id :name]}]
                           {:values [[1 "Sean"] [2 "Jay"]]}]
                    :select [:id :name]
                    :from [:stuff]})
["WITH stuff (id, name) AS (VALUES (?, ?), (?, ?)) SELECT id, name FROM stuff" 1 "Sean" 2 "Jay"]
```

`:with-recursive` follows the same rules as `:with` and produces `WITH RECURSIVE` instead of just `WITH`.

> Note: HoneySQL 0.6.2 introduced support for CTEs a long time ago and it expected the pair (of result set and query) to be wrapped in a sequence, even though you can only have a single CTE. For backward compatibility, HoneySQL 2.0 accepts that format but it should be considered deprecated.

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

## insert-into

There are two use cases with `:insert-into`. The first case
takes just a simple SQL entity (the table name). The more
complex case takes a pair of a SQL entity and a SQL query.
In that second case, you can specify the columns by using
a pair of the table name and a sequence of column names.

For the first case, you'll use the `:values` clause and you
may use the `:columns` clause as well.

```clojure
user=> (sql/format {:insert-into :transport
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
user=> (sql/format {:insert-into :transport
                    :columns [:id :name]
                    :values [[1 "Car"] [2 "Boat"] [3 "Bike"]]})
["INSERT INTO transport (id, name) VALUES (?, ?), (?, ?), (?, ?)" 1 "Car" 2 "Boat" 3 "Bike"]
```

The second case:

```clojure
user=> (sql/format '{insert-into (transport {select (id, name) from (cars)})})
["INSERT INTO transport SELECT id, name FROM cars"]
user=> (sql/format '{insert-into ((transport (id, name)) {select (*) from (cars)})})
["INSERT INTO transport (id, name) SELECT * FROM cars"]
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

## using

## join, left-join, right-join, inner-join, outer-join, full-join

## cross-join

## set (MySQL)

## where

## group-by

## having

## order-by

## limit, offset (MySQL)

## for

## lock (MySQL)

## values

## on-conflict, on-constraint, do-nothing, do-update-set

## returning
