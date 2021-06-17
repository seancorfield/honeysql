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
[honey.sql.helpers](https://cljdoc.org/d/com.github.seancorfield/honeysql/2.0.0-rc3/api/honey.sql.helpers) namespace.
If you're migrating to HoneySQL 2.x, this [overview of differences
between 1.x and 2.x](differences-from-1-x.md) should help.
