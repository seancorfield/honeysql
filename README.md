# Honey SQL

SQL as Clojure data structures.

**Work in progress**

## Usage

```clj
(require '[honeysql.core :refer [format-sql sql merge-sql sql-fn sql-raw]])

;; Everything is centered around maps representing SQL queries
(def sqlmap {:select [:a :b :c]
             :from [:foo]
             :where [:= :f.a "baz"]})

;; format-sql turns maps into clojure.java.jdbc-compatible, parameterized SQL
(format-sql sqlmap)
=> ["SELECT a, b, c FROM foo WHERE (f.a = ?)" ["baz"]]

;; The sql function is a helper for building query maps
(= sqlmap
   (sql :select [:a :b :c]
        :from :foo
        :where [:= :f.a "baz"]))
=> true

;; Providing a map as the first argument to sql will use that map as a base,
;; with the new clauses replacing old ones
(format-sql (sql sqlmap :select :* :limit 10))
=> ["SELECT * FROM foo WHERE (f.a = ?) LIMIT 10" ["baz"]]

;; To add to clauses instead of replacing them, use merge-sql
(format-sql
  (merge-sql sqlmap :select [:d :e] :where [:> :b 10]))
=> ["SELECT a, b, c, d, e FROM foo WHERE ((f.a = ?) AND (b > 10))" ["baz"]]

;; Queries can be nested
(format-sql
  (sql :select :*
       :from :foo
       :where [:in :foo.a (sql :select :a
                               :from :bar)]))
=> ["SELECT * FROM foo WHERE (foo.a IN (SELECT a FROM bar))"]

;; There are helper functions and data literals for handling SQL function
;; calls and raw SQL fragments
(sql :select [(sql-fn :count :*) (sql-raw "@var := foo.bar")]
     :from :foo)
=> {:from (:foo), :select (#sql/fn [:count :*] #sql/raw "@var := foo.bar")}

(format-sql *1)
=> ["SELECT COUNT(*), @var := foo.bar FROM foo"]

```

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
