# XTDB Support

As of 2.6.1230, HoneySQL provides support for most of XTDB's SQL
extensions, with additional support being added in subsequent releases.

For the most part, XTDB's SQL is based on
[SQL:2011](https://en.wikipedia.org/wiki/SQL:2011), including the
bitemporal features, but also includes a number of SQL extensions
to support additional XTDB-specific features.

HoneySQL attempts to support all of these XTDB features in the core
ANSI dialect, and this section documents most of those XTDB features.

For more details, see the XTDB documentation:
* [SQL Overview](https://docs.xtdb.com/quickstart/sql-overview.html)
* [SQL Queries](https://docs.xtdb.com/reference/main/sql/queries.html)
* [SQL Transactions/DML](https://docs.xtdb.com/reference/main/sql/txs.html)

## Code Examples

The code examples herein assume:
```clojure
(refer-clojure :exclude '[update set])
(require '[honey.sql :as sql]
         '[honey.sql.helpers :refer [select from where
                                     delete-from erase-from
                                     insert-into patch-into values
                                     records]])
```

Clojure users can opt for the shorter `(require '[honey.sql :as sql] '[honey.sql.helpers :refer :all])` but this syntax is not available to ClojureScript users.

## `select` Variations

XTDB allows you to omit `SELECT` in a query. `SELECT *` is assumed if
it is omitted. In HoneySQL, you can simply omit the `:select` clause
from the DSL to achieve this.

```clojure
user=> (sql/format '{select * from foo where (= status "active")})
["SELECT * FROM foo WHERE status = ?" "active"]
user=> (sql/format '{from foo where (= status "active")})
["FROM foo WHERE status = ?" "active"]
```

You can also `SELECT *` and then exclude columns and/or rename columns.

```clojure
user=> (sql/format {:select [[:* {:exclude :_id :rename [[:title, :name]]}]]})
["SELECT * EXCLUDE _id RENAME title AS name"]
user=> (sql/format '{select ((a.* {exclude _id})
                             (b.* {rename ((title, name))}))
                     from ((foo a))
                     join ((bar b) (= a._id b.foo_id))})
["SELECT a.* EXCLUDE _id, b.* RENAME title AS name FROM foo AS a INNER JOIN bar AS b ON a._id = b.foo_id"]
```

`:exclude` can accept a single column, or a sequence of columns.
`:rename` accepts a sequence of pairs (column name, new name).

```clojure
user=> (sql/format {:select [[:* {:exclude [:_id :upc]
                                  :rename [[:title, :name]
                                           [:price, :cost]]}]]})
["SELECT * EXCLUDE (_id, upc) RENAME (title AS name, price AS cost)"]
```

## Nested Sub-Queries

XTDB can produce structured results from `SELECT` queries containing
sub-queries, using `NEST_ONE` and `NEST_MANY`. In HoneySQL, these are
supported as regular function syntax in `:select` clauses.

```clojure
user=> (sql/format '{select (a.*
                             ((nest_many {select * from bar where (= foo_id a._id)})
                              b))
                     from ((foo a))})
["SELECT a.*, NEST_MANY (SELECT * FROM bar WHERE foo_id = a._id) AS b FROM foo AS a"]
```

Remember that function calls in `:select` clauses need to be nested three
levels of parentheses (brackets):
`:select [:col-a [:col-b :alias-b] [[:fn-call :col-c] :alias-c]]`.

## `records` Clause

XTDB provides a `RECORDS` clause to specify a list of structured documents,
similar to `VALUES` but specifically for documents rather than a collection
of column values. HoneySQL supports a `:records` clauses and automatically
lifts hash map values to parameters (rather than treating them as DSL fragments).
You can inline a hash map to produce XTDB's inline document syntax.
See also `insert` and `patch` below.

```clojure
user=> (sql/format {:records [{:_id 1 :status "active"}]})
["RECORDS ?" {:_id 1, :status "active"}]
user=> (sql/format {:records [[:inline {:_id 1 :status "active"}]]})
["RECORDS {_id: 1, status: 'active'}"]
```

## `object` (`record`) Literals

While `RECORDS` exists in parallel to the `VALUES` clause, XTDB also provides
a syntax to construct documents in other contexts in SQL, via the `OBJECT`
literal syntax. `RECORD` is a synonym for `OBJECT`. HoneySQL supports both
`:object` and `:record` as special syntax:

```clojure
user=> (sql/format {:select [[[:object {:_id 1 :status "active"}]]]})
["SELECT OBJECT (_id: 1, status: 'active')"]
user=> (sql/format {:select [[[:record {:_id 1 :status "active"}]]]})
["SELECT RECORD (_id: 1, status: 'active')"]
```

A third option is to use `:inline` with a hash map:

```clojure
user=> (sql/format {:select [[[:inline {:_id 1 :status "active"}]]]})
["SELECT {_id: 1, status: 'active'}"]
```

## Object Navigation Expressions

In order to deal with nested documents, XTDB provides syntax to navigate
into them, via field names and/or array indices. HoneySQL supports this
via the `:get-in` special syntax, intended to be familiar to Clojure users.

The first argument to `:get-in` is treated as an expression that produces
the document, and subsequent arguments are treated as field names or array
indices to navigate into that document.

```clojure
user=> (sql/format {:select [[[:get-in :doc :field1 :field2]]]})
["SELECT (doc).field1.field2"]
user=> (sql/format {:select [[[:get-in :table.col 0 :field]]]})
["SELECT (table.col)[0].field"]
```

If you want an array index to be a parameter, use `:lift`:

```clojure
user=> (sql/format {:select [[[:get-in :doc [:lift 0] :field]]]})
["SELECT (doc)[?].field" 0]
```

## Temporal Queries

XTDB allows any query to be run in a temporal context via the `SETTING`
clause (ahead of the `SELECT` clause). HoneySQL supports this via the
`:setting` clause. It accepts a sequence of identifiers and expressions.
An identifier ending in `-time` is assumed to be a temporal identifier
(e.g., `:system-time` mapping to `SYSTEM_TIME`). Other identifiers are assumed to
be regular SQL (so `-` is mapped to a space, e.g., `:as-of` mapping to `AS OF`).
A timestamp literal, such as `DATE '2024-11-24'` can be specified in HoneySQL
using `[:inline [:DATE "2024-11-24"]]` (note the literal case of `:DATE`
to produce `DATE`).

See [XTDB's Top-level queries documentation](https://docs.xtdb.com/reference/main/sql/queries.html#_top_level_queries) for more details.

Here's one fairly complex example:

```clojure
user=> (sql/format {:setting [[:snapshot-time :to [:inline :DATE "2024-11-24"]]
                              [:default :valid-time :to :between [:inline :DATE "2022"] :and [:inline :DATE "2023"]]]})
["SETTING SNAPSHOT_TIME TO DATE '2024-11-24', DEFAULT VALID_TIME TO BETWEEN DATE '2022' AND DATE '2023'"]
```

Table references (e.g., in a `FROM` clause) can also have temporal qualifiers.
See [HoneySQL's `from` clause documentation](clause-reference.md#from) for
examples of that, one of which is reproduced here:

```clojure
user=> (sql/format {:select [:username]
                    :from [[:user :for :system-time :as-of [:inline "2019-08-01 15:23:00"]]]
                    :where [:= :id 9]})
["SELECT username FROM user FOR SYSTEM_TIME AS OF '2019-08-01 15:23:00' WHERE id = ?" 9]
```

## `delete` and `erase`

In XTDB, `DELETE` is a temporal deletion -- the data remains in the database
but is no longer visible in queries that don't specify a time range prior to
the deletion. XTDB provides a similar `ERASE` operation that can permanently
delete the data. HoneySQL supports `:erase-from` with the same syntax as
`:delete-from`.

```clojure
user=> (sql/format {:delete-from :foo :where [:= :status "inactive"]})
["DELETE FROM foo WHERE status = ?" "inactive"]
user=> (sql/format {:erase-from :foo :where [:= :status "inactive"]})
["ERASE FROM foo WHERE status = ?" "inactive"]
```

## `insert` and `patch`

XTDB supports `PATCH` as an upsert operation: it will update existing
documents (via merging the new data) or insert new documents if they
don't already exist. HoneySQL supports `:patch-into` with the same syntax
as `:insert-into` with `:records`.

```clojure
user=> (sql/format {:insert-into :foo
                    :records [{:_id 1 :status "active"}]})
["INSERT INTO foo RECORDS ?" {:_id 1, :status "active"}]
user=> (sql/format {:patch-into :foo
                    :records [{:_id 1 :status "active"}]})
["PATCH INTO foo RECORDS ?" {:_id 1, :status "active"}]
```

## `assert`

XTDB supports an `ASSERT` operation that will throw an exception if the
asserted predicate is not true:

```clojure
user=> (sql/format '{assert (not-exists {select 1 from users where (= email "james @example.com")})}
                   :inline true)
["ASSERT NOT EXISTS (SELECT 1 FROM users WHERE email = 'james @example.com')"]
```

## Inline XTQL

As of 2.7.next, HoneySQL supports inline XTQL queries via the `:xtql` special
syntax. It can be used both as the whole query and embedded inside SQL:

```clojure
user=> (sql/format [:xtql '(-> (from :my-table [x]) (where (<= x 100)))])
["XTQL $$ (-> (from :my-table [x]) (where (<= x 100))) $$"]
user=> (sql/format {:from [[[:xtql '(-> (from :my-table [x]) (where (<= x 100)))] :my_table]]})
["FROM (XTQL $$ (-> (from :my-table [x]) (where (<= x 100))) $$) AS my_table"]
```
