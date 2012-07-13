# Honey SQL

SQL as Clojure data structures.

**Work in progress**

## Usage

```clj
(refer-clojure :exclude '[group-by])
(require '[honeysql.core
           :as sql
           :refer [select from where join group-by having order-by
                   offset limit modifiers merge-select merge-where]])
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
=> ["SELECT a, b, c FROM foo WHERE (f.a = ?)" ["baz"]]
```

There are helper functions to build SQL maps. They compose together nicely:

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
(sql/format
  (-> sqlmap
      (merge-select :d :e)
      (merge-where [:> :b 10])))
=> ["SELECT a, b, c, d, e FROM foo WHERE ((f.a = ?) AND (b > 10))" ["baz"]]
```

Queries can be nested:

```clj
(sql/format
  (-> (select :*)
      (from :foo)
      (where [:in :foo.a (-> (select :a) (from :bar))])))
=> ["SELECT * FROM foo WHERE (foo.a IN (SELECT a FROM bar))"]
```

There are helper functions and data literals for handling SQL function
calls and raw SQL fragments:

```clj
(-> (select (sql/call :count :*) (sql/raw "@var := foo.bar"))
    (from :foo))
=> {:from (:foo), :select (#sql/call [:count :*] #sql/raw "@var := foo.bar")}

(sql/format *1)
=> ["SELECT COUNT(*), @var := foo.bar FROM foo"]
```

Here's a big, complicated query. (Note that Honey SQL makes no attempt to verify that your queries are valid):

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
    (group-by :f.a)
    (having [:< 0 :f.e])
    (order-by [:b.baz :desc] :c.quux)
    (limit 50)
    (offset 10))

(sql/format *1)
=> ["SELECT DISTINCT f.*, b.baz, c.quux, NOW(), @x := 10 FROM foo AS f, baz AS b LEFT JOIN clod AS c ON (f.a = c.d) JOIN draq ON (f.b = draq.x) WHERE (((f.a = ?) AND (b.baz != ?)) OR (f.e IN (1, 2, 3)) OR f.e BETWEEN 10 AND 20) GROUP BY f.a HAVING (0 < f.e) ORDER BY b.baz DESC, c.quux LIMIT 50 OFFSET 10"
    ["bort" "gabba"]]

;; Printable and readable
(= *2 (read-string (pr-str *2)))
=> true
```

## TODO

* Date/time helpers
* Insert, update, delete
* Create table, etc.

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
