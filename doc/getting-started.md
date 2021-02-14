# Getting Started with HoneySQL

HoneySQL lets you build complex SQL statements by constructing
and composing Clojure data structures and then formatting that
data to a SQL statement (string) and any parameters it needs.

## Installation

For the Clojure CLI, add the following dependency to your `deps.edn` file:

```clojure
    seancorfield/honeysql {:mvn/version "2.0.0-alpha1"}
```

For Leiningen, add the following dependency to your `project.clj` file:

```clojure
    [seancorfield/honeysql "2.0.0-alpha1"]
```

> Note: 2.0.0-alpha1 will be released shortly!

HoneySQL produces SQL statements but does not execute them.
To execute SQL statements, you will also need a JDBC wrapper like
[`seancorfield/next.jdbc`](https://github.com/seancorfield/next-jdbc) and a JDBC driver for the database you use.

## Basic Concepts

SQL statements are represented as hash maps, with keys that
represent clauses in SQL. SQL expressions are generally
represented as vectors, where the first element identifies
the function or operator and the remaining elements are the
arguments or operands.

`honey.sql/format` takes a hash map representing a SQL
statement and produces a vector, suitable for use with
`next.jdbc` or `clojure.java.jdbc`, that has the generated
SQL string as the first element followed by any parameter
values identified in the SQL expressions:

```clojure
(ns my.example
  (:require [honey.sql :as sql]))

(sql/format {:select [:*], :from [:table], :where [:= :id 1]})
;; produces:
;;=> ["SELECT * FROM table WHERE id = ?" 1]
```

By default, any values found in the data structure, that are not keywords
or symbols, are treated as positional parameters and replaced
by `?` in the SQL string and lifted out into the vector that
is returned from `format`.

Most clauses expect a vector as their value, containing
either a list of SQL entities or the representation of a SQL
expression. Some clauses accept a single SQL entity. A few
accept a most specialized form (such as `:set` accepting a
hash map of SQL entities and SQL expressions).

A SQL entity can be a simple keyword (or symbol) or a pair
that represents a SQL entity and its alias (where aliases are allowed):

```clojure
(sql/format {:select [:t.id [:name :item]], :from [[:table :t]], :where [:= :id 1]})
;; produces:
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

The `FROM` clause now has a pair that identifies the SQL entity
`table` and its alias `t`. Columns can be identified either by
their qualified name (as in `:t.id`) or their unqualified name
(as in `:name`). The `SELECT` clause here identifies two SQL
entities: `t.id` and `name` with the latter aliased to `item`.

Symbols can also be used, but you need to quote them to
avoid evaluation:

```clojure
(sql/format '{select [t.id [name item]], from [[table t]], where [= id 1]})
;; or you can use (..) instead of [..] when quoted:
(sql/format '{select (t.id (name item)), from ((table t)), where (= id 1)})
;; also produces:
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

If you wish, you can specify SQL entities as namespace-qualified
keywords (or symbols) and the namespace portion will treated as
the table name, i.e., `:foo/bar` instead of `:foo.bar`:

```clojure
(sql/format {:select [:t/id [:name :item]], :from [[:table :t]], :where [:= :id 1]})
;; and
(sql/format '{select [t/id [name item]], from [[table t]], where [= id 1]})
;; both produce:
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

In addition to the hash map (and vectors) approach of building
SQL queries with raw Clojure data structures, a namespace full
of helper functions is also available. These functions are
generally variadic and threadable:

```clojure
(ns my.example
  (:require [honey.sql :as sql]
            [honey.sql.helpers :refer [select from where]]))

(-> (select :t/id [:name :item])
    (from [:table :t])
    (where [:= :id 1])
    (sql/format))
;; produces:
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

There is a helper function for every single clause that HoneySQL
supports out of the box. In addition, there are helpers for
`composite` and `over` that make it easier to construct those
parts of the SQL DSL (examples of the former appear in the [README.md](README),
examples of the latter appear in the [docs/clause-reference.md](Clause Reference))

In addition to being variadic -- which often lets you omit one
level of `[`..`]` -- the helper functions merge clauses, which
can make it easier to build queries programmatically:

```clojure
(-> (select :t/id)
    (from [:table :t])
    (where [:= :id 1])
    (select [:name :item])
    (sql/format))
;; produces:
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

If you want to replace a clause with a subsequent helper call,
you need to explicitly remove the prior value:

```clojure
(-> (select :t/id)
    (from [:table :t])
    (where [:= :id 1])
    (dissoc :select)
    (select [:name :item])
    (sql/format))
;; produces:
;;=> ["SELECT name AS item FROM table AS t WHERE id = ?" 1]
```

Helpers always use keywords when constructing clauses so you
can rely on using keywords in `dissoc`.

The following helpers shadow functions in `clojure.core` so
you need to consider this when referring symbols in from the
`honey.sql.helpers` namespace: `for`, `group-by`, `partition-by`,
`set`, and `update`.

## Reference Documentation

The full list of supported SQL clauses is documented in the
[docs/clause-reference.md](Clause Reference). The full list
of "special syntax" functions is documented in the
[docs/special-syntax.md](Special Syntax) section. The best
documentation for the helper functions is the
[honey.sql.helpers](https://cljdoc.org/d/seancorfield/honeysql/CURRENT/api/honey.sql.helpers).
If you're migrating to HoneySQL 2.0, this [overview of differences
between 1.0 and 2.0](docs/differences-from-1-x.md) should help.
