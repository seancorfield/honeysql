# General Reference Documentation

This section provides more details about specific behavior in HoneySQL and
how to generate certain SQL constructs.

## SQL Entity Generation

HoneySQL treats keywords and symbols as SQL entities (in any context other
than function call position in a sequence). If quoting is in effect,
either because `:dialect` was specified as an option to `format` or
because `:quoted true` was specified, the literal name of an unqualified,
single-segment keyword or symbol is used as-is and quoted:

```clojure
(require '[honey.sql :as sql])

(sql/format {:select :foo-bar} {:quoted true})
;;=> ["SELECT \"foo-bar\""]
(sql/format {:select :foo-bar} {:dialect :mysql})
;;=> ["SELECT `foo-bar`"]
```

If quoting is not in effect, any dashes (`-`) in the name will be converted to underscores (`_`):

```clojure
(sql/format {:select :foo-bar})
;;=> ["SELECT foo_bar"]
(sql/format {:select :foo-bar} {:dialect :mysql :quoted false})
;;=> ["SELECT foo_bar"]
```

If a keyword or symbol contains a dot (`.`), it will be split apart
and treated as a table (or alias) name and a column name:

```clojure
(sql/format {:select :foo-bar.baz-quux} {:quoted true})
;;=> ["SELECT \"foo-bar\".\"baz-quux\""]
(sql/format {:select :foo-bar.baz-quux} {:dialect :mysql})
;;=> ["SELECT `foo-bar`.`baz-quux`"]
(sql/format {:select :foo-bar.baz-quux})
;;=> ["SELECT foo_bar.baz_quux"]
(sql/format {:select :foo-bar.baz-quux} {:dialect :mysql :quoted false})
;;=> ["SELECT foo_bar.baz_quux"]
```

A qualified keyword or symbol, will also be split apart, but dashes (`-`)
in the namespace portion _will_ be converted to underscores (`_`) even
when quoting is in effect:

```clojure
(sql/format {:select :foo-bar/baz-quux} {:quoted true})
;;=> ["SELECT \"foo_bar\".\"baz-quux\""] ; _ in table, - in column
(sql/format {:select :foo-bar/baz-quux} {:dialect :mysql})
;;=> ["SELECT `foo_bar`.`baz-quux`"] ; _ in table, - in column
(sql/format {:select :foo-bar/baz-quux})
;;=> ["SELECT foo_bar.baz_quux"] ; _ in table and _ in column
(sql/format {:select :foo-bar/baz-quux} {:dialect :mysql :quoted false})
;;=> ["SELECT foo_bar.baz_quux"] ; _ in table and _ in column
```

Finally, there are some contexts where only a SQL entity is accepted, rather than an
arbitrary SQL expression, so a string will also be treated as a SQL entity and in such cases
the entity name will always be quoted, dashes (`-`) will not be converted to
underscores (`_`), and a slash (`/`) is not treated as separating a
qualifier from the name, regardless of the `:dialect` or `:quoted` settings:

```clojure
(sql/format {:update :table :set {"foo-bar" 1 "baz/quux" 2}})
;;=> ["UPDATE table SET \"foo-bar\" = ?, \"baz/quux\" = ?" 1 2]
(sql/format {:update :table :set {"foo-bar" 1 "baz/quux" 2}} {:quoted true})
;;=> ["UPDATE \"table\" SET \"foo-bar\" = ?, \"baz/quux\" = ?" 1 2]
(sql/format {:update :table :set {"foo-bar" 1 "baz/quux" 2}} {:dialect :mysql})
;;=> ["UPDATE `table` SET `foo-bar` = ?, `baz/quux` = ?" 1 2]
(sql/format {:update :table :set {"foo-bar" 1 "baz/quux" 2}} {:dialect :sqlserver :quoted false})
;;=> ["UPDATE table SET [foo-bar] = ?, [baz/quux] = ?" 1 2]
```

## Tuples and Composite Values

Some databases support "composite values" which are usually
represented as tuples in SQL, eg., `(col1,col2)` or `(13,42,'foo')`.
In HoneySQL 1.x, you could sometimes get away with just using a
vector of entities and/or values, but it was very much dependent
on the context. HoneySQL 2.x always treats vectors (and sequences)
as function calls (which may be "special syntax" or an actual
function call).

HoneySQL provides `:composite` as special syntax to construct
these tuples:

```clojure
(sql/format-expr [:composite :col1 :col2])
;;=> ["(col1, col2)"]
(sql/format-expr [:composite 13 42 "foo"])
;;=> ["(?, ?, ?)" 13 42 "foo"]
;; or using symbols:
(sql/format-expr '(composite col1 col2))
;;=> ["(col1, col2)"]
(sql/format-expr '(composite 13 42 "foo"))
;;=> ["(?, ?, ?)" 13 42 "foo"]
```

There is also a `composite` helper function.

## Working with JSON/JSONB (PostgreSQL)

It is increasingly common for PostgreSQL users to be working with JSON columns
in their databases these days. PostgreSQL has really good support for JSON types.

When using HoneySQL to generate SQL that manipulates JSON, you need to be careful
because it is common to use regular Clojure data structures to represent the JSON
and rely on protocol extensions for the JDBC libraries to handle automatic
conversion of Clojure data structures to JSON (e.g., see
[Tips & Tricks > Working with JSON and JSONB](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/CURRENT/doc/getting-started/tips-tricks#working-with-json-and-jsonb) in the `next.jdbc`
documentation).

HoneySQL also uses Clojure data structures, to represent function calls (vectors) and
SQL statements (hash maps), so if you are also using Clojure data structures for your
JSON, you need to tell HoneySQL not to interpret those values. There
are two possible approaches:

1. Use named parameters (e.g., `[:param :myval]`) instead of having the values directly in the DSL structure and then pass `{:params {:myval some-json}}` as part of the options in the call to `format`, or
2. Use `[:lift ..]` wrapped around any structured values which tells HoneySQL not to interpret the vector or hash map value as a DSL: `[:lift some-json]`.

## Caching

As of 2.2.858, `format` can cache the SQL and parameters produced from the data structure so that it does not need to be computed on every call. This functionality is available only in Clojure and depends on [`org.clojure/core.cache`](https://github.com/clojure/core.cache) being on your classpath. If you are repeatedly building the same complex SQL statements over and over again, this can be a good way to provide a performance boost but there are some caveats.

* You need `core.cache` as a dependency: `org.clojure/core.cache {:mvn/version "1.0.225"}` was the latest as of January 20th, 2022,
* You need to create one or more caches yourself, from the various factory functions in the [`clojure.core.cache.wrapped` namespace](http://clojure.github.io/core.cache/#clojure.core.cache.wrapped),
* You should use named parameters in your SQL DSL data structure, e.g., `:?foo` or `'?foo`, and pass the actual parameter values via the `:params` option to `format`.

You can then pass the (atom containing the) cache to `format` using the `:cache` option. The call to `format` then looks in that cache for a match for the data structure passed in, i.e., the entire data structure is used as a key into the cache, including any literal parameter values. If the cache contains a match, the corresponding vector of a SQL string and parameters is used, otherwise the data structure is parsed as usual and the SQL string (and parameters) generated from it (and stored in the cache for the next call). Finally, named parameters in the vector are replaced by their values from the `:params` option.

The code that _builds_ the DSL data structure will be run in all cases, so any conditional logic and helper function calls will still happen, since that is how the data structure is created and then passed to `format`. If you want to avoid overhead, you'd need to take steps to build the data structure separately and store it somewhere for reuse in the call to `format`.

Since the data structure is used as the key into the cache, literal parameter values will lead to different keys:

<!-- :test-doc-blocks/skip -->
```clojure
;; these are two different cache entries:
(sql/format {:select :* :from :table :where [:= :id 1]} {:cache my-cache})
(sql/format {:select :* :from :table :where [:= :id 2]} {:cache my-cache})
;; these are the same cache entry:
(sql/format {:select :* :from :table :where [:= :id :?id]} {:cache my-cache :params {:id 1}})
(sql/format {:select :* :from :table :where [:= :id :?id]} {:cache my-cache :params {:id 2}})
```

Since HoneySQL accepts any of the `clojure.core.cache.wrapped` caches and runs every data structure through the provided `:cache`, it's up to you to ensure that your cache is appropriate for that usage: a "basic" cache will keep every entry until the cache is explicitly emptied; a TTL cache will keep each entry for a specific period of time; and so on.

## Other Sections Will Be Added!

As questions arise about the use of HoneySQL 2.x, I will add new sections here.

## Other Reference Documentation

The full list of supported SQL clauses is documented in the
[Clause Reference](clause-reference.md). The full list
of operators supported (as prefix-form "functions") is
documented in the [Operator Reference](operator-reference.md)
section. The full list
of "special syntax" functions is documented in the
[Special Syntax](special-syntax.md) section. The best
documentation for the helper functions is in the
[honey.sql.helpers](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/api/honey.sql.helpers) namespace.
If you're migrating to HoneySQL 2.x, this [overview of differences
between 1.x and 2.x](differences-from-1-x.md) should help.
