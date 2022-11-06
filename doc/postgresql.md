# PostgreSQL Support

This section covers the PostgreSQL-specific
features that HoneySQL supports out of the box
for which you previously needed the
[nilenso/honeysql-postgres](https://github.com/nilenso/honeysql-postgres)
library.

Everything that the nilenso library provided (in 0.4.112) is implemented
directly in HoneySQL 2.x although a few things have a
slightly different syntax.

## Code Examples

The code examples herein assume:
```clojure
(refer-clojure :exclude '[update set])
(require '[honey.sql :as sql]
         '[honey.sql.helpers :refer [select from where
                                     update set
                                     insert-into values
                                     create-table with-columns create-view create-extension
                                     add-column alter-table add-index
                                     alter-column rename-column rename-table
                                     drop-table drop-column drop-index drop-extension
                                     upsert returning on-conflict on-constraint
                                     do-update-set do-nothing]])
```

Clojure users can opt for the shorter `(require '[honey.sql :as sql] '[honey.sql.helpers :refer :all])` but this syntax is not available to ClojureScript users.

## Working with Arrays

HoneySQL supports `:array` as special syntax to produce `ARRAY[..]` expressions
but PostgreSQL also has an "array constructor" for creating arrays from subquery results.

```sql
SELECT ARRAY(SELECT oid FROM pg_proc WHERE proname LIKE 'bytea%');
```

In order to produce that SQL, you can use HoneySQL's "as-is" function syntax to circumvent
the special syntax:

```clojure
user=> (sql/format {:select [[[:'ARRAY {:select :oid :from :pg_proc :where [:like :proname [:inline "bytea%"]]}]]]})
["SELECT ARRAY (SELECT oid FROM pg_proc WHERE proname LIKE 'bytea%')"]
```

Compare this with the `ARRAY[..]` syntax:

```clojure
user=> (sql/format {:select [[[:array [1 2 3]] :a]]})
["SELECT ARRAY[?, ?, ?] AS a" 1 2 3]
```

## Operators with @, #, and ~

A number of PostgreSQL operators contain `@`, `#`, or `~` which are not legal in a Clojure keyword or symbol (as literal syntax). The namespace `honey.sql.pg-ops` provides convenient symbolic names for these JSON and regex operators, substituting `at` for `@`, `hash` for `#`, and `tilde` for `~`.

The regex operators also have more memorable aliases: `regex` for `~`, `iregex` for `~*`, `!regex` for `!~`, and `!iregex` for `!~*`.

Requiring the namespace automatically registers these operators for use in expressions:

```clojure
user=> (require '[honey.sql.pg-ops :refer [regex]])
nil
user=> (sql/format {:select [[[regex :straw [:inline "needle"]] :match]] :from :haystack})
["SELECT straw ~ 'needle' AS match FROM haystack"]
```

## JSON/JSONB

If you are using JSON with PostgreSQL, you will probably try to pass Clojure
data structures as values into your HoneySQL DSL -- but HoneySQL will see those
vectors as function calls and hash maps as SQL statements, so you need to tell
HoneySQL not to do that. There are two possible approaches:

1. Use named parameters (e.g., `[:param :myval]`) instead of having the values directly in the DSL structure and then pass `{:params {:myval some-json}}` as part of the options in the call to `format`, or
2. Use `[:lift ..]` wrapped around any structured values which tells HoneySQL not to interpret the vector or hash map value as a DSL: `[:lift some-json]`.

## Upsert

Upserting data is relatively easy in PostgreSQL
because of the `ON CONFLICT`, `ON CONSTRAINT`,
`DO NOTHING`, and `DO UPDATE SET` parts of the
`INSERT` statement.

This usage is supported identically to the nilenso library:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 5 :dname "Gizmo Transglobal"}
                    {:did 6 :dname "Associated Computing, Inc"}])
           (upsert (-> (on-conflict :did)
                       (do-update-set :dname)))
           (returning :*)
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?), (?, ?)
ON CONFLICT (did)
DO UPDATE SET dname = EXCLUDED.dname
RETURNING *
"
5 "Gizmo Transglobal"
6 "Associated Computing, Inc"]
```

However, the nested `upsert` helper is no longer needed
(and there is no corresponding `:upsert` clause in the DSL):

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 5 :dname "Gizmo Transglobal"}
                    {:did 6 :dname "Associated Computing, Inc"}])
           (on-conflict :did)
           (do-update-set :dname)
           (returning :*)
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?), (?, ?)
ON CONFLICT (did)
DO UPDATE SET dname = EXCLUDED.dname
RETURNING *
"
5 "Gizmo Transglobal"
6 "Associated Computing, Inc"]
```

Similarly, the `do-nothing` helper behaves just the same
as in the nilenso library:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 7 :dname "Redline GmbH"}])
           (upsert (-> (on-conflict :did)
                       do-nothing))
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?)
ON CONFLICT (did)
DO NOTHING
"
7 "Redline GmbH"]
```

As above, the nested `upsert` helper is no longer needed:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 7 :dname "Redline GmbH"}])
           (on-conflict :did)
           do-nothing
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?)
ON CONFLICT (did)
DO NOTHING
"
7 "Redline GmbH"]
```

`ON CONSTRAINT` is handled slightly differently to the nilenso library,
which provided a single `on-conflict-constraint` helper (and clause):

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 9 :dname "Antwerp Design"}])
           ;; can specify as a nested clause...
           (on-conflict (on-constraint :distributors_pkey))
           do-nothing
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?)
ON CONFLICT ON CONSTRAINT distributors_pkey
DO NOTHING
"
9 "Antwerp Design"]
user=> (-> (insert-into :distributors)
           (values [{:did 9 :dname "Antwerp Design"}])
           ;; ...or as two separate clauses
           on-conflict
           (on-constraint :distributors_pkey)
           do-nothing
           (sql/format {:pretty true}))
["
INSERT INTO distributors
(did, dname) VALUES (?, ?)
ON CONFLICT
ON CONSTRAINT distributors_pkey
DO NOTHING
"
9 "Antwerp Design"]
```

As above, the `upsert` helper has been omitted here.

An upsert with where clauses is also possible, with a
more compact syntax than the nilenso library used:

```clojure
user=> (-> (insert-into :user)
           (values [{:phone "5555555" :name "John"}])
           (on-conflict :phone (where [:<> :phone nil]))
           (do-update-set :phone :name (where [:= :user.active false]))
           (sql/format {:pretty true}))
["
INSERT INTO user
(phone, name) VALUES (?, ?)
ON CONFLICT (phone) WHERE phone IS NOT NULL
DO UPDATE SET phone = EXCLUDED.phone, name = EXCLUDED.name WHERE user.active = FALSE
"
"5555555" "John"]
;; using the DSL directly:
user=> (sql/format
        {:insert-into    :user
          :values        [{:phone "5555555" :name "John"}]
          :on-conflict   [:phone
                          {:where [:<> :phone nil]}]
          :do-update-set {:fields [:phone :name]
                          :where  [:= :user.active false]}}
        {:pretty true})
["
INSERT INTO user
(phone, name) VALUES (?, ?)
ON CONFLICT (phone) WHERE phone IS NOT NULL
DO UPDATE SET phone = EXCLUDED.phone, name = EXCLUDED.name WHERE user.active = FALSE
"
"5555555" "John"]
```

By comparison, this is the DSL structure that nilenso would have required:

<!-- :test-doc-blocks/skip -->
```clojure
  ;; NOT VALID FOR HONEYSQL!
  {:insert-into :user
   :values      [{:phone "5555555" :name "John"}]
   ;; nested under :upsert
   :upsert      {:on-conflict   [:phone]
                 ;; but :where is at the same level as :on-conflict
                 :where         [:<> :phone nil]
                 ;; this is the same as in honeysql:
                 :do-update-set {:fields [:phone :name]
                                 :where  [:= :user.active false]}}}
```

## INSERT INTO AS

HoneySQL supports aliases directly in `:insert-into` so no special
clause is needed for this any more:

```clojure
user=> (sql/format (-> (insert-into :table :alias)
                       (values [[1 2 3] [4 5 6]])))
["INSERT INTO table AS alias VALUES (?, ?, ?), (?, ?, ?)" 1 2 3 4 5 6]
user=> (sql/format {:insert-into [:table :alias],
                    :values [[1 2 3] [4 5 6]]})
["INSERT INTO table AS alias VALUES (?, ?, ?), (?, ?, ?)" 1 2 3 4 5 6]
```

## Returning

The `RETURNING` clause is supported identically to the nilenso library:

```clojure
;; via the DSL:
user=> (sql/format {:delete-from :distributors
                    :where [:> :did 10]
                    :returning [:*]})
["DELETE FROM distributors WHERE did > ? RETURNING *" 10]
;; via the helpers:
user=> (-> (update :distributors)
           (set {:dname "Foo Bar Designs"})
           (where [:= :did 2])
           (returning :did :dname)
           sql/format)
["UPDATE distributors SET dname = ? WHERE did = ? RETURNING did, dname"
 "Foo Bar Designs" 2]
```

## DDL Support

The following DDL statements are all supported by HoneySQL
(these are mostly not PostgreSQL-specific but they were not
supported by HoneySQL 1.x):

* `CREATE VIEW`
* `CREATE TABLE`
* `DROP TABLE`
* `ALTER TABLE`

These are mostly identical to what the nilenso library provides
except that `sql/call` is never needed -- you can use the direct
`[:func ..]` function call syntax instead:

```clojure
;; create view:
user=> (-> (create-view :metro)
           (select :*)
           (from :cities)
           (where [:= :metroflag "Y"])
           sql/format)
["CREATE VIEW metro AS SELECT * FROM cities WHERE metroflag = ?" "Y"]
;; create table:
user=> (-> (create-table :cities)
           (with-columns [[:city [:varchar 80] [:primary-key]]
                          [:location :point]])
           sql/format)
;; values are inlined:
["CREATE TABLE cities (city VARCHAR(80) PRIMARY KEY, location POINT)"]
;; default values for columns:
user=> (-> (create-table :distributors)
           (with-columns [[:did :integer [:primary-key]
                                         ;; "serial" is inlined as 'serial':
                                         [:default [:nextval "serial"]]]
                          [:name [:varchar 40] [:not nil]]])
           (sql/format {:pretty true}))
;; newlines inserted for readability:
["
CREATE TABLE distributors
(did INTEGER PRIMARY KEY DEFAULT NEXTVAL('serial'), name VARCHAR(40) NOT NULL)
"]
;; PostgreSQL CHECK constraint is supported:
user=> (-> (create-table :products)
           (with-columns [[:product_no :integer]
                          [:name :text]
                          [:price :numeric [:check [:> :price 0]]]
                          [:discounted_price :numeric]
                          [[:check [:and [:> :discounted_price 0] [:> :price :discounted_price]]]]])
           (sql/format {:pretty true}))
["
CREATE TABLE products
(product_no INTEGER, name TEXT, price NUMERIC CHECK(price > 0), discounted_price NUMERIC, CHECK((discounted_price > 0) AND (price > discounted_price)))
"]
;; conditional creation:
user=> (-> (create-table :products :if-not-exists)
           (with-columns [[:name :text]])
           sql/format)
["CREATE TABLE IF NOT EXISTS products (name TEXT)"]
;; drop table:
user=> (sql/format (drop-table :cities))
["DROP TABLE cities"]
;; drop multiple tables:
user=> (sql/format (drop-table :cities :towns :vilages))
["DROP TABLE cities, towns, vilages"]
;; conditional drop:
user=> (sql/format (drop-table :if-exists :cities :towns :vilages))
["DROP TABLE IF EXISTS cities, towns, vilages"]
;; alter table add column:
user=> (-> (alter-table :fruit)
           (add-column :skin [:varchar 16] nil)
           sql/format)
["ALTER TABLE fruit ADD COLUMN skin VARCHAR(16) NULL"]
;; alter table drop column:
user=> (-> (alter-table :fruit)
           (drop-column :skin)
           sql/format)
["ALTER TABLE fruit DROP COLUMN skin"]
;; alter table alter column:
user=> (-> (alter-table :fruit)
           (alter-column :name [:varchar 64] [:not nil])
           sql/format)
["ALTER TABLE fruit ALTER COLUMN name VARCHAR(64) NOT NULL"]
;; alter table rename column:
user=> (-> (alter-table :fruit)
           (rename-column :cost :price)
           sql/format)
["ALTER TABLE fruit RENAME COLUMN cost TO price"]
;; rename table:
user=> (-> (alter-table :fruit)
           (rename-table :vegetable)
           sql/format)
["ALTER TABLE fruit RENAME TO vegetable"]
```

The following PostgreSQL-specific DDL statements are supported
(with the same syntax as the nilenso library but `sql/format`
takes slightly different options):

```clojure
;; create extension:
user=> (-> (create-extension :uuid-ossp)
           (sql/format {:quoted true}))
;; quoting is required for a name containing a hyphen:
["CREATE EXTENSION \"uuid-ossp\""]
;; conditional creation:
user=> (-> (create-extension :uuid-ossp :if-not-exists)
           (sql/format {:quoted true}))
["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""]
;; drop extension:
user=> (-> (drop-extension :uuid-ossp)
           (sql/format {:quoted true}))
["DROP EXTENSION \"uuid-ossp\""]
;; drop multiple extensions:
user=> (-> (drop-extension :uuid-ossp :postgis)
           (sql/format {:quoted true}))
["DROP EXTENSION \"uuid-ossp\", \"postgis\""]
;; conditional drop:
user=> (-> (drop-extension :if-exists :uuid-ossp :postgis)
           (sql/format {:quoted true}))
["DROP EXTENSION IF EXISTS \"uuid-ossp\", \"postgis\""]
```

In addition, HoneySQL supports these DDL statements that were
not supported by the nilenso library:

```clojure
;; alter table add index:
user=> (-> (alter-table :fruit)
           (add-index :unique :fruit-name :name)
           sql/format)
["ALTER TABLE fruit ADD UNIQUE fruit_name(name)"]
;; alter table drop index:
user=> (-> (alter-table :fruit)
           (drop-index :fruit-name)
           sql/format)
["ALTER TABLE fruit DROP INDEX fruit_name"]
;; alter table with multiple clauses:
user=> (sql/format (alter-table :fruit
                                (add-column :skin [:varchar 16] nil)
                                (add-index :unique :fruit-name :name)))
["ALTER TABLE fruit ADD COLUMN skin VARCHAR(16) NULL, ADD UNIQUE fruit_name(name)"]
```

## Filter / Within Group

`honeysql-postgres` added support for `FILTER` and `WITHIN GROUP`
in its 0.4.112 release. Those features have been integrated into
HoneySQL 2.x (as of 2.0.0-beta2), along with support for `ORDER BY`
in expressions. `:filter`, `:within-group`, and `:order-by` are
all available as "functions" in [Special Syntax](special-syntax.md),
and there are helpers for `filter` and `within-group`.

## Window / Partition Support

HoneySQL supports `:window`, `:partition-by`, and `:over`
directly now.
See the Clause Reference for examples of [WINDOW, PARTITION BY, and OVER](clause-reference.md#window-partition-by-and-over).
