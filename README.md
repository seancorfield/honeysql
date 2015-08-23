# Honey SQL

SQL as Clojure data structures. Build queries programmatically -- even at runtime -- without having to bash strings together.

## Build

[![Build Status](https://travis-ci.org/jkk/honeysql.svg?branch=master)](https://travis-ci.org/jkk/honeysql)
[![Dependencies Status](http://jarkeeper.com/jkk/honeysql/status.svg)](http://jarkeeper.com/jkk/honeysql)

## Leiningen Coordinates

[![Clojars Project](http://clojars.org/honeysql/latest-version.svg)](http://clojars.org/honeysql)

## Usage

```clj
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all])
```

Everything is built on top of maps representing SQL queries:

```clj
(def sqlmap {:select [:a :b :c]
             :from [:foo]
             :where [:= :f.a "baz"]})
```

`format` turns maps into `clojure.java.jdbc`-compatible, parameterized SQL:

```clj
(sql/format sqlmap)
=> ["SELECT a, b, c FROM foo WHERE (f.a = ?)" "baz"]
```

You can build up SQL maps yourself or use helper functions. `build` is the Swiss Army Knife helper. It lets you leave out brackets here and there:

```clj
(sql/build :select :*
           :from :foo
           :where [:= :f.a "baz"])
=> {:where [:= :f.a "baz"], :from [:foo], :select [:*]}
```

You can provide a "base" map as the first argument to build:

```clj
(sql/build sqlmap :offset 10 :limit 10)
=> {:limit 10, :offset 10, :select [:a :b :c], :where [:= :f.a "baz"], :from [:foo]}
```

There are also functions for each clause type in the `honeysql.helpers` namespace:

```clj
(-> (select :a :b :c)
    (from :foo)
    (where [:= :f.a "baz"]))
```

Order doesn't matter:

```clj
(= (-> (select :*) (from :foo))
   (-> (from :foo) (select :*)))
=> true
```

When using the vanilla helper functions, new clauses will replace old clauses:

```clj
(-> sqlmap (select :*))
=> {:from [:foo], :where [:= :f.a "baz"], :select (:*)}
```

To add to clauses instead of replacing them, use `merge-select`, `merge-where`, etc.:

```clj
(-> sqlmap
    (merge-select :d :e)
    (merge-where [:> :b 10])
    sql/format)
=> ["SELECT a, b, c, d, e FROM foo WHERE (f.a = ? AND b > 10)" "baz"]
```

`where` will combine multiple clauses together using and:

```clj
(-> (select :*)
    (from :foo)
    (where [:= :a 1] [:< :b 100])
    sql/format)
=> ["SELECT * FROM foo WHERE (a = 1 AND b < 100)"]
```

Inserts are supported in two patterns. 
In the first pattern, you must explicitly specify the columns to insert,
then provide a collection of rows, each a collection of column values:

```clj
(-> (insert-into :properties)
    (columns :name :surname :age)
    (values
     [["Jon" "Smith" 34]
      ["Andrew" "Cooper" 12]
      ["Jane" "Daniels" 56]])
    sql/format)
=> ["INSERT INTO properties (name, surname, age)
     VALUES (?, ?, 34), (?, ?, 12), (?, ?, 56)"
     "Jon" "Smith" "Andrew" "Cooper" "Jane" "Daniels"]
```


Alternately, you can simply specify the values as maps; the first map defines the columns to insert,
and the remaining maps *must* have the same set of keys and values:

```clj
(-> (insert-into :properties)
    (values [{:name "John" :surname "Smith" :age 34}
             {:name "Andrew" :surname "Cooper" :age 12}
             {:name "Jane" :surname "Daniels" :age 56}])
    sql/format)
=> ["INSERT INTO properties (age, name, surname) 
     VALUES (34, ?, ?), (12, ?, ?), (56, ?, ?)" 
    "John" "Smith" 
    "Andrew" "Cooper" 
    "Jane" "Daniels"]
```

The column values do not have to be literals, they can be nested queries:

```clj
(let [user-id 12345
      role-name "user"]
  (-> (insert-into :user_profile_to_role)
      (values [{:user_profile_id user-id
                :role_id         (-> (select :id)
                                     (from :role)
                                     (where [:= :name role-name]))}])
      sql/format))

=> ["INSERT INTO user_profile_to_role (user_profile_id, role_id) 
     VALUES (12345, (SELECT id FROM role WHERE name = ?))" 
    "user"]
```

MySQL upserts are supported:

```clj
(-> (insert-into :properties)
    (columns :name :surname :age)
    (values
     [["Jon" "Smith" 34]
      ["Andrew" "Cooper" 12]
      ["Jane" "Daniels" 56]])
    (upsert :mysql {:age #sql/call [:values :age]})
    sql/format)
=> ["INSERT INTO properties (name, surname, age)
     VALUES (?, ?, 34), (?, ?, 12), (?, ?, 56)"
     "Jon" "Smith" "Andrew" "Cooper" "Jane" "Daniels"]
```

Updates are possible too (note the double S in `sset` to avoid clashing
with `clojure.core/set`):

```clj
(-> (update :films)
    (sset {:kind "dramatic"
           :watched true})
    (where [:= :kind "drama"])
    sql/format)
=> ["UPDATE films SET watched = TRUE, kind = ? WHERE kind = ?" "dramatic" "drama"]
```

Deletes look as you would expect:

```clj
(-> (delete-from :films)
    (where [:<> :kind "musical"])
    sql/format)
=> ["DELETE FROM films WHERE kind <> ?" "musical"]
```

Queries can be nested:

```clj
(-> (select :*)
    (from :foo)
    (where [:in :foo.a (-> (select :a) (from :bar))])
    sql/format)
=> ["SELECT * FROM foo WHERE (foo.a IN (SELECT a FROM bar))"]
```

Keywords that begin with `%` are interpreted as SQL function calls:

```clj
(-> (select :%count.*) (from :foo) sql/format)
=> ["SELECT count(*) FROM foo"]
(-> (select :%max.id) (from :foo) sql/format)
=> ["SELECT max(id) FROM foo"]
```

Keywords that begin with `?` are interpreted as bindable parameters:

```clj
(-> (select :id)
    (from :foo)
    (where [:= :a :?baz])
    (sql/format :params {:baz "BAZ"}))
=> ["SELECT id FROM foo WHERE a = ?" "BAZ"]
```

There are helper functions and data literals for SQL function calls, field qualifiers, raw SQL fragments, and named input parameters:

```clj
(-> (select (sql/call :foo :bar) (sql/qualify :foo :a) (sql/raw "@var := foo.bar"))
    (from :foo)
    (where [:= :a (sql/param :baz)]))
=> {:where [:= :a #sql/param :baz], :from (:foo), :select (#sql/call [:foo :bar] :foo.a #sql/raw "@var := foo.bar")}

(sql/format *1 :params {:baz "BAZ"})
=> ["SELECT FOO(bar), foo.a, @var := foo.bar FROM foo WHERE a = ?" "BAZ"]
```

To quote identifiers, pass the `:quoting` keyword option to `format`. Valid options are `:ansi` (PostgreSQL), `:mysql`, or `:sqlserver`:

```clj
(-> (select :foo.a)
    (from :foo)
    (where [:= :foo.a "baz"])
    (sql/format :quoting :mysql))
=> ["SELECT `foo`.`a` FROM `foo` WHERE `foo`.`a` = ?" "baz"]
```

To issue a locking select, add a :lock to the query or use the lock helper. The lock value must be a map with a :mode value. The built-in
modes are the standard :update (FOR UPDATE) or the vendor-specific :mysql-share (LOCK IN SHARE MODE) or :postresql-share (FOR SHARE). The
lock map may also provide a :wait value, which if false will append the NOWAIT parameter, supported by PostgreSQL.

```clj
(-> (select :foo.a)
    (from :foo)
    (where [:= foo.a "baz"])
    (lock {:mode :update})
    (sql/format))
=> ["SELECT foo.a FROM foo WHERE foo.a = ? FOR UPDATE" "baz"]
```

To support novel lock modes, implement the `format-lock-clause` multimethod.

Here's a big, complicated query. Note that Honey SQL makes no attempt to verify that your queries make any sense. It merely renders surface syntax.

```clj
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
    (offset 10))
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
    :order-by [[:b.baz :desc] :c.quux [:f.a :nulls-first]
    :limit 50
    :offset 10}

(sql/format *1 {:param1 "gabba" :param2 2})
=> ["SELECT DISTINCT f.*, b.baz, c.quux, b.bla AS \"bla-bla\", NOW(), @x := 10
     FROM foo AS f, baz AS b
     INNER JOIN draq ON f.b = draq.x
     LEFT JOIN clod AS c ON f.a = c.d
     RIGHT JOIN bock ON bock.z = c.e
     WHERE ((f.a = ? AND b.baz <> ?)
           OR (1 < 2 AND 2 < 3)
           OR (f.e IN (1, ?, 3))
           OR f.e BETWEEN 10 AND 20)
     GROUP BY f.a
     HAVING 0 < f.e
     ORDER BY b.baz DESC, c.quux, f.a NULLS FIRST
     LIMIT 50
     OFFSET 10 "
     "bort" "gabba" 2]

;; Printable and readable
(= *2 (read-string (pr-str *2)))
=> true
```

## Extensibility

You can define your own function handlers for use in `where`:

```clj
(require '[honeysql.format :as fmt])

(defmethod fmt/fn-handler "betwixt" [_ field lower upper]
  (str (fmt/to-sql field) " BETWIXT "
       (fmt/to-sql lower) " AND " (fmt/to-sql upper)))

(-> (select :a) (where [:betwixt :a 1 10]) sql/format)
=> ["SELECT a WHERE a BETWIXT 1 AND 10"]

```

You can also define your own clauses:

```clj

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

If you do implement a clause or function handler, consider submitting a pull request so others can use it, too. 

## TODO

* Create table, etc.

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
