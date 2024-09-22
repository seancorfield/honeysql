# Getting Started with HoneySQL

HoneySQL lets you build complex SQL statements by constructing
and composing Clojure data structures and then formatting that
data to a SQL statement (string) and any parameters it needs.

## Installation

For the Clojure CLI, add the following dependency to your `deps.edn` file:

<!-- :test-doc-blocks/skip -->
```clojure
    com.github.seancorfield/honeysql {:mvn/version "2.6.1161"}
```

For Leiningen, add the following dependency to your `project.clj` file:

<!-- :test-doc-blocks/skip -->
```clojure
    [com.github.seancorfield/honeysql "2.6.1161"]
```

HoneySQL produces SQL statements but does not execute them.
To execute SQL statements, you will also need a JDBC wrapper like
[`seancorfield/next.jdbc`](https://github.com/seancorfield/next-jdbc) and a JDBC driver for the database you use.

You can also experiment with HoneySQL directly in a browser -- no installation
required -- using [John Shaffer](https://github.com/john-shaffer)'s awesome
[HoneySQL web app](https://www.john-shaffer.com/honeysql/), written in ClojureScript!

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
(require '[honey.sql :as sql])

(sql/format {:select [:*], :from [:table], :where [:= :id 1]})
;;=> ["SELECT * FROM table WHERE id = ?" 1]
```

By default, any values found in the data structure, that are not keywords
or symbols, are treated as positional parameters and replaced
by `?` in the SQL string and lifted out into the vector that
is returned from `format`.

Most clauses expect a vector as their value, containing
either a list of SQL entities or the representation of a SQL
expression. Some clauses accept a single SQL entity. A few
accept a more specialized form (such as `:set` within an `:update` clause
accepting a hash map of SQL entities and SQL expressions).

> Note: clauses can have a list as their value, but literal vectors and keywords are easier to type without quoting.

A SQL entity can be a simple keyword (or symbol) or a pair
that represents a SQL entity and its alias (where aliases are allowed):

```clojure
(sql/format {:select [:t.id [:name :item]], :from [[:table :t]], :where [:= :id 1]})
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
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]

;; or you can use (..) instead of [..] when quoted to produce the same result:
(sql/format '{select (t.id (name item)), from ((table t)), where (= id 1)})
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

> Note: these quoted forms may be appealing to users familiar with Datalog-family query languages, and they can be easier to type (and read) in some cases since you do not need to add `:` (shift-`;` on most keyboards) to the start of each SQL entity. The quoted forms do not work well in the [HoneySQL web app](https://john.shaffe.rs/honeysql/) so it's better to stick with vectors and keywords when using that.

If you wish, you can specify SQL entities as namespace-qualified
keywords (or symbols) and the namespace portion will treated as
the table name, i.e., `:foo/bar` instead of `:foo.bar`:

```clojure
;; notice the following both produce the same result:
(sql/format {:select [:t/id [:name :item]], :from [[:table :t]], :where [:= :id 1]})
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
(sql/format '{select [t/id [name item]], from [[table t]], where [= id 1]})
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

## SQL Expressions

In addition to using hash maps to describe SQL clauses,
HoneySQL uses vectors to describe SQL expressions. Any
vector that begins with a keyword (or symbol) is considered
to be a kind of function invocation. Certain "functions" are
considered to be "special syntax" and have custom rendering.
Some "functions" are considered to be operators. In general,
`[:foo :a 42 "c"]` will render as `FOO(a, ?, ?)` with the parameters
`42` and `"c"` lifted out into the overall vector result
(with a SQL string followed by all its parameters).

> Note: you can use the `:numbered true` option to `format` to produce SQL containing numbered placeholders, like `FOO(a, $1, $2)`, instead of positional placeholders (`?`).

As of 2.4.1002, function calls with "named" arguments are supported
which some databases support, e.g., MySQL and PostgreSQL both have
`SUBSTRING()`:

<!-- :test-doc-blocks/skip -->
```clojure
[:substring :col 3 4]              ;=> SUBSTRING(col, 3, 4)
;; can also be written:
[:substring :col :!from 3 :!for 4] ;=> SUBSTRING(col FROM 3 FOR 4)
```

In a function call, any keywords (or symbols) that begin with `!` followed
by a letter are treated as inline SQL keywords to be used instead of `,`
between arguments -- or in front of arguments, such as for `TRIM()`:

<!-- :test-doc-blocks/skip -->
```clojure
[:trim :!leading "x" :!from :col] ;=> TRIM(LEADING ? FROM col), with "x" parameter
[:trim :!both :!from :col]        ;=> TRIM(BOTH FROM col), trims spaces
;; adjacent inline SQL keywords can be combined with a hyphen:
[:trim :!both-from :col]          ;=> TRIM(BOTH FROM col)
;; (because - in a SQL keyword is replaced by a space)
```

Operators are all treated as variadic (except for `:=` and
`:<>` / `:!=` / `:not=` which are binary and require exactly two operands).
Special syntax can have zero or more arguments and each form is
described in the [Special Syntax](special-syntax.md) section.

Some examples:

<!-- :test-doc-blocks/skip -->
```clojure
[:= :a 42]                              ;=> "a = ?" with a parameter of 42
[:+ 42 :a :b]                           ;=> "? + a + b" with a parameter of 42
[:= :x [:inline "foo"]]                 ;=> "x = 'foo'" -- the string is inlined
[:now]                                  ;=> "NOW()"
[:count :*]                             ;=> "COUNT(*)"
[:or [:<> :name nil] [:= :status-id 0]] ;=> "(name IS NOT NULL) OR (status_id = ?)"
;; the nil value is inlined as NULL but 0 is provided as a parameter
```

`:inline` is an example of "special syntax" and it renders its
arguments as part of the SQL string generated by `format`.

Another form of special syntax that is treated as function calls
is keywords or symbols that begin with `%`. Such keywords (or quoted symbols)
are split at `.` and turned into function calls:

<!-- :test-doc-blocks/skip -->
```clojure
:%now     ;=> NOW()
:%count.* ;=> COUNT(*)
:%max.foo ;=> MAX(foo)
:%f.a.b   ;=> F(a,b)
```

If you need to reference a table or alias for a column, you can use
qualified names in a function invocation:

<!-- :test-doc-blocks/skip -->
```clojure
%max.foo/bar ;=> MAX(foo.bar)
```

The latter syntax can be convenient in a `SELECT` because `[:a :b]` is
otherwise taken as a column and its alias, so selecting a function call
expression requires an extra level of nesting:

```clojure
(sql/format {:select [:a]})
;;=> ["SELECT a"]
(sql/format {:select [[:a :b]]})
;;=> ["SELECT a AS b"]
(sql/format {:select [[[:a :b]]]})
;;=> ["SELECT A(b)"]
;; or use the % notification:
(sql/format {:select [:%a.b]})
;;=> ["SELECT A(b)"]
(sql/format {:select [[[:a :b] :c]]})
;;=> ["SELECT A(b) AS c"]
(sql/format {:select [[:%a.b :c]]})
;;=> ["SELECT A(b) AS c"]
;; putting it all together:
(sql/format {:select [:x [:y :d] [[:z :e]] [[:z :f] :g]]})
;;=> ["SELECT x, y AS d, Z(e), Z(f) AS g"]
(sql/format {:select [:x [:y :d] [:%z.e] [:%z.f :g]]})
;;=> ["SELECT x, y AS d, Z(e), Z(f) AS g"]
(sql/format {:select [:x [:y :d] :%z.e [:%z.f :g]]})
;;=> ["SELECT x, y AS d, Z(e), Z(f) AS g"]
```

## SQL Parameters

As indicated in the preceding sections, values found in the DSL data structure
that are not keywords or symbols are lifted out as positional parameters.
By default, they are replaced by `?` in the generated SQL string and added to the
parameter list in order:

<!-- :test-doc-blocks/skip -->
```clojure
[:between :size 10 20] ;=> "size BETWEEN ? AND ?" with parameters 10 and 20
```

If you specify the `:numbered true` option to `format`, numbered placeholders (`$1`, `$2`, etc) will be used instead of positional placeholders (`?`).

<!-- :test-doc-blocks/skip -->
```clojure
;; with :numbered true option:
[:between :size 10 20] ;=> "size BETWEEN $1 AND $2" with parameters 10 and 20
```

HoneySQL also supports named parameters. There are two ways
of identifying a named parameter:
* a keyword or symbol that begins with `?`
* the `:param` special (functional) syntax

The values of those parameters are supplied in the `format`
call as the `:params` key of the options hash map.

```clojure
(sql/format {:select [:*] :from [:table]
             :where [:= :a :?x]}
            {:params {:x 42}})
;;=> ["SELECT * FROM table WHERE a = ?" 42]
(sql/format {:select [:*] :from [:table]
             :where [:= :a [:param :x]]}
            {:params {:x 42}})
;;=> ["SELECT * FROM table WHERE a = ?" 42]
```

Or with `:numbered true`:
```clojure
(sql/format {:select [:*] :from [:table]
             :where [:= :a :?x]}
            {:params {:x 42} :numbered true})
;;=> ["SELECT * FROM table WHERE a = $1" 42]
(sql/format {:select [:*] :from [:table]
             :where [:= :a [:param :x]]}
            {:params {:x 42} :numbered true})
;;=> ["SELECT * FROM table WHERE a = $1" 42]
```

## Functional Helpers

In addition to the hash map (and vectors) approach of building
SQL queries with raw Clojure data structures, a
[namespace full of helper functions](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/api/honey.sql.helpers)
is also available. These functions are generally variadic and threadable:

```clojure
(require '[honey.sql :as sql]
         '[honey.sql.helpers :refer [select from where]])

(-> (select :t/id [:name :item])
    (from [:table :t])
    (where [:= :id 1])
    (sql/format))
;;=> ["SELECT t.id, name AS item FROM table AS t WHERE id = ?" 1]
```

There is a helper function for every single clause that HoneySQL
supports out of the box. In addition, there are helpers for
`composite`, `lateral`, `over`, and `upsert` that make it easier to construct those
parts of the SQL DSL (examples of `composite` appear in the [README](/README.md),
examples of `over` appear in the [Clause Reference](clause-reference.md))

In general, `(helper :foo expr)` will produce `{:helper [:foo expr]}`
(with a few exceptions -- see the docstring of the helper function
for details).

In addition to being variadic -- which often lets you omit one
level of `[`..`]` -- the helper functions merge clauses, which
can make it easier to build queries programmatically:

```clojure
(-> (select :t/id)
    (from [:table :t])
    (where [:= :id 1])
    (select [:name :item])
    (sql/format))
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
;;=> ["SELECT name AS item FROM table AS t WHERE id = ?" 1]
```

Helpers always use keywords when constructing clauses so you
can rely on using keywords in `dissoc`.

The following helpers shadow functions in `clojure.core` so
you need to consider this when referring symbols in from the
`honey.sql.helpers` namespace: `filter`, `for`, `group-by`, `into`, `partition-by`,
`set`, and `update`.

## DDL Statements

HoneySQL 1.x did not support any DDL statements. It was fairly
common for people to use the
[nilenso/honeysql-postgres library](https://github.com/nilenso/honeysql-postgres)
to get DDL support, even if they didn't need the PostgreSQL-specific
extensions. That library does not work with HoneySQL 2.x but all
of the functionality from it (up to 0.4.112) has been incorporated
into HoneySQL now and is described in the [PostgreSQL](postgresql.md)
section (because that covers all of the things that the nilenso
library supported and much of it was PostgreSQL-specific!).

See also the [DDL Clauses section](clause-reference.md#ddl-clauses) of
the Clause Reference for documentation about supported DDL.

## Dialects

By default, HoneySQL operates in ANSI SQL mode but it supports
a lot of PostgreSQL extensions in that mode. PostgreSQL is mostly
a superset of ANSI SQL so it makes sense to support as much as
possible of the union of ANSI SQL and PostgreSQL out of the box.

The dialects supported by HoneySQL 2.x are:
* `:ansi` -- the default, including most PostgreSQL extensions
* `:sqlserver` -- Microsoft SQL Server
* `:mysql` -- MySQL (and Percona and MariaDB)
* `:nrql` -- as of 2.5.1091
* `:oracle` -- Oracle

The most visible difference between dialects is how SQL entities
should be quoted (if the `:quoted true` option is provided to `format`).
Most databases use `"` for quoting (the `:ansi` and `:oracle` dialects).
The `:sqlserver` dialect uses `[`..`]` and the `:mysql` dialect uses
```` .. ````. In addition, the `:oracle` dialect disables `AS` in aliases.

> Note: by default, quoting is **off** which produces cleaner-looking SQL and assumes you control all the symbols/keywords used as table, column, and function names -- the "SQL entities". If you are building any SQL or DDL where the table, column, or function names could be provided by an external source, **you should specify `:quoted true` to ensure all SQL entities are safely quoted**. As of 2.3.928, if you do _not_ specify `:quoted` as an option, HoneySQL will automatically quote any SQL entities that seem unusual, i.e., that contain any characters that are not alphanumeric or underscore. Purely alphanumeric entities will not be quoted (no entities were quoted by default prior to 2.3.928). You can prevent that auto-quoting by explicitly passing `:quoted false` into the `format` call but, from a security point of view, you should think very carefully before you do that: quoting entity names helps protect you from injection attacks! As of 2.4.947, you can change the default setting of `:quoted` from `nil` to `true` (or `false`) via the `set-options!` function.

Currently, the only dialect that has substantive differences from
the others is `:mysql` for which the `:set` clause
has a different precedence than ANSI SQL.

See [New Relic NRQL Support](nrsql.md) for more details of the NRQL dialect.

You can change the dialect globally using the `set-dialect!` function,
passing in one of the keywords above. You need to call this function
before you call `format` for the first time.

You can change the dialect for a single `format` call by
specifying the `:dialect` option in that call.

Alphanumeric SQL entities are not quoted by default but if you specify the
dialect in a `format` call, they will be quoted. If you don't
specify a dialect in the `format` call, you can specify
`:quoted true` to have SQL entities quoted. You can also enable quoting
globally via the `set-dialect!` function.

If you want to use a dialect _and_ use the default quoting strategy (automatically quote any SQL entities that seem unusual), specify a `:dialect` option and set `:quoted nil`:

<!-- Reminder to doc author:
     Reset dialect to default so other blocks are not affected for test-doc-blocks -->
```clojure
(sql/format '{select (id) from (table)} {:quoted true})
;;=> ["SELECT \"id\" FROM \"table\""]
(sql/format '{select (id) from (table)} {:dialect :mysql})
;;=> ["SELECT `id` FROM `table`"]
(sql/set-dialect! :sqlserver)
;;=> nil
(sql/format '{select (id) from (table)} {:quoted true})
;;=> ["SELECT [id] FROM [table]"]
;; you can also choose to enable quoting globally
;; when you set a dialect:
(sql/set-dialect! :mysql :quoted true)
(sql/format '{select (id) from (table)})
;;=> ["SELECT `id` FROM `table`"]
;; and opt out for a specific call:
(sql/format '{select (id) from (table)} {:quoted false})
;;=> ["SELECT id FROM table"]
;; and reset back to the default of :ansi
(sql/set-dialect! :ansi)
;;=> nil
;; which also resets the quoting default (back to nil)
;; so only unusual entity names get quoted:
(sql/format '{select (id) from (table)} {:quoted true})
;;=> ["SELECT \"id\" FROM \"table\""]
;; use default quoting strategy with dialect specific quotes, only unusual entities quoted
(sql/format '{select (id, iffy##field ) from (table)} {:dialect :sqlserver :quoted nil})
;; => ["SELECT id, [iffy##field] FROM table"]
```

Out of the box, as part of the extended ANSI SQL support,
HoneySQL supports quite a few [PostgreSQL extensions](postgresql.md).

> Note: the [nilenso/honeysql-postgres](https://github.com/nilenso/honeysql-postgres) library which provided PostgreSQL support for HoneySQL 1.x does not work with HoneySQL 2.x. However, HoneySQL 2.x includes all of the functionality from that library (up to 0.4.112) out of the box!

See also the section on
[database-specific hints and tips](databases.md) which may
provide ways to satisfy your database's needs without changing
the dialect or extending HoneySQL.

## Reference Documentation

The full list of supported SQL clauses is documented in the
[Clause Reference](clause-reference.md). The full list
of operators supported (as prefix-form "functions") is
documented in the [Operator Reference](operator-reference.md)
section. The full list
of "special syntax" functions is documented in the
[Special Syntax](special-syntax.md) section. The best
documentation for the helper functions is in the
[honey.sql.helpers](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/api/honey.sql.helpers) namespace.
More detail about certain core HoneySQL functionality can be found in the
[Reference documentation](general-reference.md).
If you're migrating to HoneySQL 2.x, this [overview of differences
between 1.x and 2.x](differences-from-1-x.md) should help.
