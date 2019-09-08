# Honey SQL

SQL as Clojure data structures. Build queries programmatically -- even at runtime -- without having to bash strings together.

## Build

[![Build Status](https://travis-ci.org/jkk/honeysql.svg?branch=master)](https://travis-ci.org/jkk/honeysql)
[![Dependencies Status](https://versions.deps.co/jkk/honeysql/status.svg)](https://versions.deps.co/jkk/honeysql)
[![project chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://clojurians.zulipchat.com/#narrow/stream/152091-honeysql)

## Leiningen Coordinates

[![Clojars Project](http://clojars.org/honeysql/latest-version.svg)](http://clojars.org/honeysql)

## Note on code samples

All sample code in this README is automatically run as a unit test using
[midje-readme](https://github.com/boxed/midje-readme).

Note that while some of these samples show pretty-printed SQL, this is just for
README readability; honeysql does not generate pretty-printed SQL.
The #sql/regularize directive tells the test-runner to ignore the extraneous
whitespace.

## Usage

```clojure
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all :as helpers])
```

Everything is built on top of maps representing SQL queries:

```clojure
(def sqlmap {:select [:a :b :c]
             :from   [:foo]
             :where  [:= :f.a "baz"]})
```

Column names can be provided as keywords or symbols (but not strings -- HoneySQL treats strings as values that should be lifted out of the SQL as parameters).

`format` turns maps into `clojure.java.jdbc`-compatible, parameterized SQL:

```clojure
(sql/format sqlmap)
=> ["SELECT a, b, c FROM foo WHERE f.a = ?" "baz"]
```

By default, namespace-qualified keywords as treated as simple keywords: their namespace portion is ignored. This was the behavior in HoneySQL prior to the 0.9.0 release and has been restored since the 0.9.7 release as this is considered the least surprising behavior.
As of version 0.9.7, `format` accepts `:allow-namespaced-names? true` to provide the somewhat unusual behavior of 0.9.0-0.9.6, namely that namespace-qualified keywords were passed through into the SQL "as-is", i.e., with the `/` in them (which generally required a quoting strategy as well).
As of version 0.9.8, `format` accepts `:namespace-as-table? true` to treat namespace-qualified keywords as if the `/` were `.`, allowing `:table/column` as an alternative to `:table.column`. This approach is likely to be more compatible with code that uses libraries like [`next.jdbc`](https://github.com/seancorfield/next-jdbc) and [`seql`](https://github.com/exoscale/seql), as well as being more convenient in a world of namespace-qualified keywords, following the example of `clojure.spec` etc.

```clojure
(def q-sqlmap {:select [:foo/a :foo/b :foo/c]
               :from   [:foo]
               :where  [:= :foo/a "baz"]})
(sql/format q-sqlmap :namespace-as-table? true)
=> ["SELECT foo.a, foo.b, foo.c FROM foo WHERE foo.a = ?" "baz"]
```

Honeysql is a relatively "pure" library, it does not manage your sql connection
or run queries for you, it simply generates SQL strings. You can then pass them
to jdbc:

```clj
(jdbc/query conn (sql/format sqlmap))
```

You can build up SQL maps yourself or use helper functions. `build` is the Swiss Army Knife helper. It lets you leave out brackets here and there:

```clojure
(sql/build :select :*
           :from :foo
           :where [:= :f.a "baz"])
=> {:where [:= :f.a "baz"], :from [:foo], :select [:*]}
```

You can provide a "base" map as the first argument to build:

```clojure
(sql/build sqlmap :offset 10 :limit 10)
=> {:limit 10
    :offset 10
    :select [:a :b :c]
    :where [:= :f.a "baz"]
    :from [:foo]}
```

There are also functions for each clause type in the `honeysql.helpers` namespace:

```clojure
(-> (select :a :b :c)
    (from :foo)
    (where [:= :f.a "baz"]))
```

Order doesn't matter:

```clojure
(= (-> (select :*) (from :foo))
   (-> (from :foo) (select :*)))
=> true
```

When using the vanilla helper functions, new clauses will replace old clauses:

```clojure
(-> sqlmap (select :*))
=> '{:from [:foo], :where [:= :f.a "baz"], :select (:*)}
```

To add to clauses instead of replacing them, use `merge-select`, `merge-where`, etc.:

```clojure
(-> sqlmap
    (merge-select :d :e)
    (merge-where [:> :b 10])
    sql/format)
=> ["SELECT a, b, c, d, e FROM foo WHERE (f.a = ? AND b > ?)" "baz" 10]
```

`where` will combine multiple clauses together using and:

```clojure
(-> (select :*)
    (from :foo)
    (where [:= :a 1] [:< :b 100])
    sql/format)
=> ["SELECT * FROM foo WHERE (a = ? AND b < ?)" 1 100]
```

Column and table names may be aliased by using a vector pair of the original
name and the desired alias:

```clojure
(-> (select :a [:b :bar] :c [:d :x])
    (from [:foo :quux])
    (where [:= :quux.a 1] [:< :bar 100])
    sql/format)
=> ["SELECT a, b AS bar, c, d AS x FROM foo quux WHERE (quux.a = ? AND bar < ?)" 1 100]
```

In particular, note that `(select [:a :b])` means `SELECT a AS b` rather than
`SELECT a, b` -- `select` is variadic and does not take a collection of column names.

Inserts are supported in two patterns.
In the first pattern, you must explicitly specify the columns to insert,
then provide a collection of rows, each a collection of column values:

```clojure
(-> (insert-into :properties)
    (columns :name :surname :age)
    (values
     [["Jon" "Smith" 34]
      ["Andrew" "Cooper" 12]
      ["Jane" "Daniels" 56]])
    sql/format)
=> [#sql/regularize
    "INSERT INTO properties (name, surname, age)
     VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)"
    "Jon" "Smith" 34 "Andrew" "Cooper" 12 "Jane" "Daniels" 56]
```


Alternately, you can simply specify the values as maps; the first map defines the columns to insert,
and the remaining maps *must* have the same set of keys and values:

```clojure
(-> (insert-into :properties)
    (values [{:name "John" :surname "Smith" :age 34}
             {:name "Andrew" :surname "Cooper" :age 12}
             {:name "Jane" :surname "Daniels" :age 56}])
    sql/format)
=> [#sql/regularize
    "INSERT INTO properties (name, surname, age)
     VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)"
    "John" "Smith" 34
    "Andrew" "Cooper"  12
    "Jane" "Daniels" 56]
```

The column values do not have to be literals, they can be nested queries:

```clojure
(let [user-id 12345
      role-name "user"]
  (-> (insert-into :user_profile_to_role)
      (values [{:user_profile_id user-id
                :role_id         (-> (select :id)
                                     (from :role)
                                     (where [:= :name role-name]))}])
      sql/format))

=> [#sql/regularize
    "INSERT INTO user_profile_to_role (user_profile_id, role_id)
     VALUES (?, (SELECT id FROM role WHERE name = ?))"
    12345
    "user"]
```

Composite types are supported:

```clojure
(-> (insert-into :comp_table)
    (columns :name :comp_column)
    (values
     [["small" (composite 1 "inch")]
      ["large" (composite 10 "feet")]])
    sql/format)
=> [#sql/regularize
    "INSERT INTO comp_table (name, comp_column)
     VALUES (?, (?, ?)), (?, (?, ?))"
    "small" 1 "inch" "large" 10 "feet"]
```

Updates are possible too (note the double S in `sset` to avoid clashing
with `clojure.core/set`):

```clojure
(-> (helpers/update :films)
    (sset {:kind "dramatic"
           :watched (sql/call :+ :watched 1)})
    (where [:= :kind "drama"])
    sql/format)
=> [#sql/regularize
    "UPDATE films SET kind = ?, watched = (watched + ?)
     WHERE kind = ?"
    "dramatic"
    1
    "drama"]
```

If you are trying to build a compound update statement (with `from` or `join`),
be aware that different databases have slightly different syntax in terms of
where `SET` should appear. The default above is to put `SET` after any `JOIN`.
There are two variants of `sset` (and the underlying `:set` in the SQL map):

* `set0` (and `:set0`) -- this puts the `SET` before `FROM`,
* `set1` (and `:set1`) -- a synonym for `sset` (and `:set`) that puts the `SET` after `JOIN`.

Deletes look as you would expect:

```clojure
(-> (delete-from :films)
    (where [:<> :kind "musical"])
    sql/format)
=> ["DELETE FROM films WHERE kind <> ?" "musical"]
```

If your database supports it, you can also delete from multiple tables:

```clojure
(-> (delete [:films :directors])
    (from :films)
    (join :directors [:= :films.director_id :directors.id])
    (where [:<> :kind "musical"])
    sql/format)
=> [#sql/regularize
    "DELETE films, directors
     FROM films
     INNER JOIN directors ON films.director_id = directors.id
     WHERE kind <> ?"
    "musical"]
```

If you want to delete everything from a table, you can use `truncate`:

```clojure
(-> (truncate :films)
    sql/format)
=> ["TRUNCATE films"]
```

Queries can be nested:

```clojure
(-> (select :*)
    (from :foo)
    (where [:in :foo.a (-> (select :a) (from :bar))])
    sql/format)
=> ["SELECT * FROM foo WHERE (foo.a in (SELECT a FROM bar))"]
```

Queries may be united within a :union or :union-all keyword:

```clojure
(sql/format {:union [(-> (select :*) (from :foo))
                     (-> (select :*) (from :bar))]})
=> ["SELECT * FROM foo UNION SELECT * FROM bar"]
```

Keywords that begin with `%` are interpreted as SQL function calls:

```clojure
(-> (select :%count.*) (from :foo) sql/format)
=> ["SELECT count(*) FROM foo"]
(-> (select :%max.id) (from :foo) sql/format)
=> ["SELECT max(id) FROM foo"]
```

Keywords that begin with `?` are interpreted as bindable parameters:

```clojure
(-> (select :id)
    (from :foo)
    (where [:= :a :?baz])
    (sql/format :params {:baz "BAZ"}))
=> ["SELECT id FROM foo WHERE a = ?" "BAZ"]
```

There are helper functions and data literals for SQL function calls, field
qualifiers, raw SQL fragments, inline values, and named input parameters:

```clojure
(def call-qualify-map
  (-> (select (sql/call :foo :bar) (sql/qualify :foo :a) (sql/raw "@var := foo.bar"))
      (from :foo)
      (where [:= :a (sql/param :baz)] [:= :b (sql/inline 42)])))

call-qualify-map
=> '{:where [:and [:= :a #sql/param :baz] [:= :b #sql/inline 42]]
     :from (:foo)
     :select (#sql/call [:foo :bar] :foo.a #sql/raw "@var := foo.bar")}

(sql/format call-qualify-map :params {:baz "BAZ"})
=> ["SELECT foo(bar), foo.a, @var := foo.bar FROM foo WHERE (a = ? AND b = 42)" "BAZ"]
```

Raw SQL fragments that are strings are treated exactly as-is when rendered into
the formatted SQL string (with no parsing or parameterization). Inline values
will not be lifted out as parameters, so they end up in the SQL string as-is.

Raw SQL can also be supplied as a vector of strings and values. Strings are
rendered as-is into the formatted SQL string. Non-strings are lifted as
parameters. If you need a string parameter lifted, you must use `#sql/param`
or the `param` helper.

```clojure
(-> (select :*)
    (from :foo)
    (where [:< :expired_at (sql/raw ["now() - '" 5 " seconds'"])])
    (sql/format {:foo 5}))
=> ["SELECT * FROM foo WHERE expired_at < now() - '? seconds'" 5]
```

```clojure
(-> (select :*)
    (from :foo)
    (where [:< :expired_at (sql/raw ["now() - '" #sql/param :t " seconds'"])])
    (sql/format {:t 5}))
=> ["SELECT * FROM foo WHERE expired_at < now() - '? seconds'" 5]
```

To quote identifiers, pass the `:quoting` keyword option to `format`. Valid options are `:ansi` (PostgreSQL), `:mysql`, or `:sqlserver`:

```clojure
(-> (select :foo.a)
    (from :foo)
    (where [:= :foo.a "baz"])
    (sql/format :quoting :mysql))
=> ["SELECT `foo`.`a` FROM `foo` WHERE `foo`.`a` = ?" "baz"]
```

To issue a locking select, add a `:lock` to the query or use the lock helper. The lock value must be a map with a `:mode` value. The built-in
modes are the standard :update (FOR UPDATE) or the vendor-specific `:mysql-share` (LOCK IN SHARE MODE) or `:postresql-share` (FOR SHARE). The
lock map may also provide a :wait value, which if false will append the NOWAIT parameter, supported by PostgreSQL.

```clojure
(-> (select :foo.a)
    (from :foo)
    (where [:= :foo.a "baz"])
    (lock :mode :update)
    (sql/format))
=> ["SELECT foo.a FROM foo WHERE foo.a = ? FOR UPDATE" "baz"]
```

To support novel lock modes, implement the `format-lock-clause` multimethod.

To be able to use dashes in quoted names, you can pass ```:allow-dashed-names true``` as an argument to the ```format``` function.
```clojure
(sql/format
  {:select [:f.foo-id :f.foo-name]
   :from [[:foo-bar :f]]
   :where [:= :f.foo-id 12345]}
  :allow-dashed-names? true
  :quoting :ansi)
=> ["SELECT \"f\".\"foo-id\", \"f\".\"foo-name\" FROM \"foo-bar\" \"f\" WHERE \"f\".\"foo-id\" = ?" 12345]
```

Here's a big, complicated query. Note that Honey SQL makes no attempt to verify that your queries make any sense. It merely renders surface syntax.

```clojure
(def big-complicated-map
  (-> (select :f.* :b.baz :c.quux [:b.bla "bla-bla"]
              (sql/call :now) (sql/raw "@x := 10"))
      (modifiers :distinct)
      (from [:foo :f] [:baz :b])
      (join :draq [:= :f.b :draq.x])
      (left-join [:clod :c] [:= :f.a :c.d])
      (right-join :bock [:= :bock.z :c.e])
      (where [:or
               [:and [:= :f.a "bort"] [:not= :b.baz (sql/param :param1)]]
               [:< 1 2 3]
               [:in :f.e [1 (sql/param :param2) 3]]
               [:between :f.e 10 20]])
      (group :f.a)
      (having [:< 0 :f.e])
      (order-by [:b.baz :desc] :c.quux [:f.a :nulls-first])
      (limit 50)
      (offset 10)))

big-complicated-map
=> {:select [:f.* :b.baz :c.quux [:b.bla "bla-bla"]
             (sql/call :now) (sql/raw "@x := 10")]
    :modifiers [:distinct]
    :from [[:foo :f] [:baz :b]]
    :join [:draq [:= :f.b :draq.x]]
    :left-join [[:clod :c] [:= :f.a :c.d]]
    :right-join [:bock [:= :bock.z :c.e]]
    :where [:or
             [:and [:= :f.a "bort"] [:not= :b.baz (sql/param :param1)]]
             [:< 1 2 3]
             [:in :f.e [1 (sql/param :param2) 3]]
             [:between :f.e 10 20]]
    :group-by [:f.a]
    :having [:< 0 :f.e]
    :order-by [[:b.baz :desc] :c.quux [:f.a :nulls-first]]
    :limit 50
    :offset 10}

(sql/format big-complicated-map {:param1 "gabba" :param2 2})
=> [#sql/regularize
    "SELECT DISTINCT f.*, b.baz, c.quux, b.bla AS bla_bla, now(), @x := 10
     FROM foo f, baz b
     INNER JOIN draq ON f.b = draq.x
     LEFT JOIN clod c ON f.a = c.d
     RIGHT JOIN bock ON bock.z = c.e
     WHERE ((f.a = ? AND b.baz <> ?)
           OR (? < ? AND ? < ?)
           OR (f.e in (?, ?, ?))
           OR f.e BETWEEN ? AND ?)
     GROUP BY f.a
     HAVING ? < f.e
     ORDER BY b.baz DESC, c.quux, f.a NULLS FIRST
     LIMIT ?
     OFFSET ? "
     "bort" "gabba" 1 2 2 3 1 2 3 10 20 0 50 10]

;; Printable and readable
(= big-complicated-map (read-string (pr-str big-complicated-map)))
=> true
```

## Extensibility

You can define your own function handlers for use in `where`:

```clojure
(require '[honeysql.format :as fmt])

(defmethod fmt/fn-handler "betwixt" [_ field lower upper]
  (str (fmt/to-sql field) " BETWIXT "
       (fmt/to-sql lower) " AND " (fmt/to-sql upper)))

(-> (select :a) (where [:betwixt :a 1 10]) sql/format)
=> ["SELECT a WHERE a BETWIXT ? AND ?" 1 10]

```

You can also define your own clauses:

```clojure

;; Takes a MapEntry of the operator & clause data, plus the entire SQL map
(defmethod fmt/format-clause :foobar [[op v] sqlmap]
  (str "FOOBAR " (fmt/to-sql v)))

(sql/format {:select [:a :b] :foobar :baz})
=> ["SELECT a, b FOOBAR baz"]

(require '[honeysql.helpers :refer [defhelper]])

;; Defines a helper function, and allows 'build' to recognize your clause
(defhelper foobar [m args]
  (assoc m :foobar (first args)))

(-> (select :a :b) (foobar :baz) sql/format)
=> ["SELECT a, b FOOBAR baz"]

```

When adding a new clause, you may also need to register it with a specific priority so that it formats correctly, for example:

```clojure
(fmt/register-clause! :foobar 110)
```

If you do implement a clause or function handler for an ANSI SQL, consider submitting a pull request so others can use it, too. For non-standard clauses and/or functions, look for a library that extends `honeysql` for that specific database or create one, if no such library exists.

## why does my parameter get emitted as `()`?

If you want to use your own datatype as a parameter then the idiomatic approach of implementing `clojure.java.jdbc`'s [`ISQLValue`](https://clojure.github.io/java.jdbc/#clojure.java.jdbc/ISQLValue) protocol isn't enough as `honeysql` won't correct pass through your datatype, rather it will interpret it incorrectly.

To teach `honeysql` how to handle your datatype you need to implement [`honeysql.format/ToSql`](https://github.com/jkk/honeysql/blob/a9dffec632be62c961be7d9e695d0b2b85732c53/src/honeysql/format.cljc#L94). For example:
``` clojure
;; given:
(defrecord MyDateWrapper [...]
  (to-sql-timestamp [this]...)
)

;; executing:
(hsql/format {:where [:> :some_column (MyDateWrapper. ...)]})
;; results in => "where :some_column > ()"

;; we can teach honeysql about it:
(extend-protocol honeysql.format/ToSql
  MyDateWrapper
  (to-sql [v] (to-sql (date/to-sql-timestamp v))))

;; allowing us to now:
(hsql/format {:where [:> :some_column (MyDateWrapper. ...)]})
;; which correctly results in => "where :some_column>?" and the parameter correctly set
```
## TODO

* Create table, etc.

## Extensions

* [For PostgreSQL-specific extensions falling outside of ANSI SQL](https://github.com/nilenso/honeysql-postgres)

## License

Copyright © 2012-2017 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
