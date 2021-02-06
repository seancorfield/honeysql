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
## select, select-distinct
## insert-into
## update
## delete, delete-from
## truncate
## columns
## set (ANSI)
## from
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