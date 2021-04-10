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

A qualified keyword or symbol, will also be split apart:

```clojure
(sql/format {:select :foo-bar/baz-quux} {:quoted true})
;;=> ["SELECT \"foo_bar\".\"baz-quux\""]
(sql/format {:select :foo-bar/baz-quux} {:dialect :mysql})
;;=> ["SELECT `foo_bar`.`baz-quux`"]
(sql/format {:select :foo-bar/baz-quux})
;;=> ["SELECT foo_bar.baz_quux"]
(sql/format {:select :foo-bar/baz-quux} {:dialect :mysql :quoted false})
;;=> ["SELECT foo_bar.baz_quux"]
```

Combining dotted names and..

## Tuples and Composite Values

Some databases support "composite values" which are usually
represented as tuples in SQL, eg., `(col1,col2)` or `(13,42,'foo')`.
In HoneySQL v1, you could sometimes get away with just using a
vector of entities and/or values, but it was very much dependent
on the context. HoneySQL v2 always treats vectors (and sequences)
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

## Other Sections Will Be Added!

## Other Reference Documentation

The full list of supported SQL clauses is documented in the
[Clause Reference](clause-reference.md). The full list
of operators supported (as prefix-form "functions") is
documented in the [Operator Reference](operator-reference.md)
section. The full list
of "special syntax" functions is documented in the
[Special Syntax](special-syntax.md) section. The best
documentation for the helper functions is in the
[honey.sql.helpers](https://cljdoc.org/d/com.github.seancorfield/honeysql/2.0.0-alpha3/api/honey.sql.helpers) namespace.
If you're migrating to HoneySQL 2.0, this [overview of differences
between 1.0 and 2.0](differences-from-1-x.md) should help.
