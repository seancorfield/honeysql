# General Reference Documentation

This section provides more details about specific behavior in HoneySQL and
how to generate certain SQL constructs.

## SQL Entity Generation

See #313

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
