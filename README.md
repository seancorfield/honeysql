# Honey SQL

Turn Clojure data structures into SQL.

**Work in progress**

## Leiningen Coordinate

```clj
[honeysql "0.1.0"]
```

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

Queries can be nested:

```clj
(-> (select :*)
    (from :foo)
    (where [:in :foo.a (-> (select :a) (from :bar))])
    sql/format)
=> ["SELECT * FROM foo WHERE (foo.a IN (SELECT a FROM bar))"]
```

There are helper functions and data literals for field qualifiers, SQL function
calls, raw SQL fragments, and named input parameters:

```clj
(-> (select (sql/qualify :foo :a) (sql/call :count :*) (sql/raw "@var := foo.bar"))
    (from :foo)
    (where [:= :a (sql/param :baz)]))
=> {:where [:= :a #sql/param :baz], :from (:foo), :select (#sql/call [:count :*] #sql/raw "@var := foo.bar")}

(sql/format *1 {:baz "BAZ"})
=> ["SELECT COUNT(*), @var := foo.bar FROM foo WHERE a = ?" "BAZ"]
```

Here's a big, complicated query. Note that Honey SQL makes no attempt to verify that your queries make any sense. It merely renders surface syntax.

```clj
(-> (select :f.* :b.baz :c.quux (sql/call :now) (sql/raw "@x := 10"))
    (modifiers :distinct)
    (from [:foo :f] [:baz :b])
    (join [[:clod :c] [:= :f.a :c.d] :left]
          [:draq [:= :f.b :draq.x]])
    (where [:or
            [:and [:= :f.a "bort"] [:not= :b.baz "gabba"]]
            [:in :f.e [1 2 3]]
            [:between :f.e 10 20]])
    (group :f.a) ;note the name change
    (having [:< 0 :f.e])
    (order-by [:b.baz :desc] :c.quux)
    (limit 50)
    (offset 10)
    sql/format)
=> ["SELECT DISTINCT f.*, b.baz, c.quux, NOW(), @x := 10 FROM foo AS f, baz AS b LEFT JOIN clod AS c ON f.a = c.d JOIN draq ON f.b = draq.x WHERE ((f.a = ? AND b.baz <> ?) OR (f.e IN (1, 2, 3)) OR f.e BETWEEN 10 AND 20) GROUP BY f.a HAVING 0 < f.e ORDER BY b.baz DESC, c.quux LIMIT 50 OFFSET 10"
   "bort" "gabba"]

;; Printable and readable
(= *1 (read-string (pr-str *1)))
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

## TODO

* Insert, update, delete
* Create table, etc.

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
