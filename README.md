# Honey SQL

SQL as Clojure data structures.

**Work in progress**

## Usage

```clj
(require '[honeysql.core :as sql :refer [select from where limit merge-select
                                         merge-where]])

;; Everything is centered around maps representing SQL queries
(def sqlmap {:select [:a :b :c]
             :from [:foo]
             :where [:= :f.a "baz"]})

;; format-sql turns maps into clojure.java.jdbc-compatible, parameterized SQL
(sql/format sqlmap)
=> ["SELECT a, b, c FROM foo WHERE (f.a = ?)" ["baz"]]

;; The sql function is a helper for building query maps
(= sqlmap
   (sql/sql :select [:a :b :c]
            :from :foo
            :where [:= :f.a "baz"]))
=> true

;; You can also use clause-specific helper functions, if you prefer. They
;; compose together nicely.
(= sqlmap
   (-> (select :a :b :c)
       (from :foo)
       (where [:= :f.a "baz"])))
=> true

;; Providing a map as the first argument to sql or clause helper functions will
;; use that map as a base, with the new clauses replacing old ones
(sql/format (sql/sql sqlmap :select :* :limit 10))
=> ["SELECT * FROM foo WHERE (f.a = ?) LIMIT 10" ["baz"]]
(sql/format (-> sqlmap (select :*) (limit 10)))
=> ["SELECT * FROM foo WHERE (f.a = ?) LIMIT 10" ["baz"]]

;; To add to clauses instead of replacing them, use merge-sql, or merge-select,
;; merge-from, etc.
(sql/format
  (sql/merge-sql sqlmap :select [:d :e] :where [:> :b 10]))
=> ["SELECT a, b, c, d, e FROM foo WHERE ((f.a = ?) AND (b > 10))" ["baz"]]
(sql/format
  (-> sqlmap (merge-select :d :e) (merge-where [:> :b 10])))
=> ["SELECT a, b, c, d, e FROM foo WHERE ((f.a = ?) AND (b > 10))" ["baz"]]

;; Queries can be nested
(sql/format
  (sql/sql :select :*
           :from :foo
           :where [:in :foo.a (sql/sql :select :a :from :bar)]))
=> ["SELECT * FROM foo WHERE (foo.a IN (SELECT a FROM bar))"]

;; There are helper functions and data literals for handling SQL function
;; calls and raw SQL fragments
(sql/sql :select [(sql/call :count :*) (sql/raw "@var := foo.bar")]
         :from :foo)
=> {:from (:foo), :select (#sql/call [:count :*] #sql/raw "@var := foo.bar")}

(sql/format *1)
=> ["SELECT COUNT(*), @var := foo.bar FROM foo"]
```

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
