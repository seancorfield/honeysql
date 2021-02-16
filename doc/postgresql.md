# PostgreSQL Support

This section covers the PostgreSQL-specific
features that HoneySQL supports out of the box
for which you previously needed the
[nilenso/honeysql-postgres library](https://github.com/nilenso/honeysql-postgres).

Everything that the nilenso library provided is implemented
directly in HoneySQL 2.x although a few things have a
slightly different syntax.

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
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?), (?, ?)
  ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING *"
 5 "Gizmo Transglobal" 6 "Associated Computing, Inc"]
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
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?), (?, ?)
  ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING *"
 5 "Gizmo Transglobal" 6 "Associated Computing, Inc"]
```

Similarly, the `do-nothing` helper behaves just the same
as in the nilenso library:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 7 :dname "Redline GmbH"}])
           (upsert (-> (on-conflict :did)
                       do-nothing))
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?)
  ON CONFLICT (did) DO NOTHING"
 7 "Redline GmbH"]
```

As above, the nested `upsert` helper is no longer needed:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 7 :dname "Redline GmbH"}])
           (on-conflict :did)
           do-nothing
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?)
  ON CONFLICT (did) DO NOTHING"
 7 "Redline GmbH"]
```

`ON CONSTRAINT` is handled slightly differently to the nilenso library:

```clojure
user=> (-> (insert-into :distributors)
           (values [{:did 9 :dname "Antwerp Design"}])
           ;; nilenso used (on-conflict-constraint :distributors_pkey) here:
           (on-conflict (on-constraint :distributors_pkey))
           do-nothing
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?)
  ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING"
 9 "Antwerp Design"]
user=> (-> (insert-into :distributors)
           (values [{:did 9 :dname "Antwerp Design"}])
           ;; nilenso used (on-conflict-constraint :distributors_pkey) here:
           on-conflict
           (on-constraint :distributors_pkey)
           do-nothing
           sql/format)
;; newlines inserted for readability:
["INSERT INTO distributors (did, dname) VALUES (?, ?)
  ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING"
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
           sql/format)
;; newlines inserted for readability:
["INSERT INTO user (phone, name) VALUES (?, ?)
  ON CONFLICT (phone) WHERE phone IS NOT NULL
  DO UPDATE SET phone = EXCLUDED.phone, name = EXCLUDED.name
  WHERE user.active = FALSE" "5555555" "John"]
;; using the DSL directly:
user=> (sql/format
        {:insert-into    :user
          :values        [{:phone "5555555" :name "John"}]
          :on-conflict   [:phone
                          {:where [:<> :phone nil]}]
          :do-update-set {:fields [:phone :name]
                          :where  [:= :user.active false]}})
;; newlines inserted for readability:
["INSERT INTO user (phone, name) VALUES (?, ?)
  ON CONFLICT (phone) WHERE phone IS NOT NULL
  DO UPDATE SET phone = EXCLUDED.phone, name = EXCLUDED.name
  WHERE user.active = FALSE" "5555555" "John"]
```

By comparison, this is the DSL structure that nilenso would have required:

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
           (returning [:did :dname])
           sql/format)
["UPDATE distributors SET dname = ? WHERE did = ? RETURNING did dname"
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
                                         ;; "serial" is inlined as 'SERIAL':
                                         [:default [:nextval "serial"]]]
                          [:name [:varchar 40] [:not nil]]])
           sql/format)
;; newlines inserted for readability:
["CREATE TABLE distributors (
  did INTEGER PRIMARY KEY DEFAULT NEXTVAL('SERIAL'),
  name VARCHAR(40) NOT NULL
)"]
;; PostgreSQL CHECK constraint is supported:
user=> (-> (create-table :products)
           (with-columns [[:product_no :integer]
                          [:name :text]
                          [:price :numeric [:check [:> :price 0]]]
                          [:discounted_price :numeric]
                          [[:check [:and [:> :discounted_price 0] [:> :price :discounted_price]]]]])
           sql/format)
;; newlines inserted for readability:
["CREATE TABLE products (
  product_no INTEGER,
  name TEXT,
  price NUMERIC CHECK(PRICE > 0),
  discounted_price NUMERIC,
  CHECK((discounted_price > 0) AND (price > discounted_price))
)"]
;; conditional creation:
user=> (-> (create-table :products :if-not-exists)
           ...
           sql/format)
["CREATE TABLE IF NOT EXISTS products (...)"]
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

;; alter table drop column:

;; alter table modify column:

;; alter table rename column:

;; rename table:

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

;; alter table drop index:

;; alter table with multiple clauses:

```

## Window / Partition Support
