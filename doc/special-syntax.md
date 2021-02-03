# SQL Special Syntax

This section lists the function-like expressions that
HoneySQL supports out of the box which are formatted
as special syntactic forms.

## array

Accepts a single argument, which is expected to evaluate to
a sequence, and produces `ARRAY[?, ?, ..]` for the elements
of that sequence (as SQL parameters):

```clojure
(sql/format-expr [:array (range 5)])
;;=> ["ARRAY[?, ?, ?, ?, ?]" 0 1 2 3 4]
```

## between

Accepts three arguments: an expression, a lower bound, and
an upper bound:

```clojure
(sql/format-expr [:between :id 1 100])
;;=> ["id BETWEEN ? AND ?" 1 100]
```

## case

A SQL CASE expression. Expects an even number of arguments:
alternating condition and result expressions. A condition
may be `:else` (or `'else`) to produce `ELSE`, otherwise
`WHEN <condition> THEN <result>` will be produced:

```clojure
(sql/format-expr [:case [:< :a 10] "small" [:> :a 100] "big" :else "medium"])
;;=> ["CASE WHEN a < ? THEN ? WHEN a > ? THEN ? ELSE ? END"
;;    10 "small" 100 "big" "medium"]
```

## cast

A SQL CAST expression. Expects an expression and something
that produces a SQL type:

```clojure
(sql/format-expr [:cast :a :int])
;;=> ["CAST(a AS int)"]
```

## composite

Accepts any number of expressions and produces a composite
expression (comma-separated, wrapped in parentheses):

```clojure
(sql/format-expr [:composite :a :b "red" [:+ :x 1]])
;;=> ["(a, b, ?, x + ?)" "red" 1]
```

## default

Takes no arguments and produces the SQL keyword `DEFAULT`.

_[I expect this to be expanded for PostgreSQL]_

## inline

Accepts a single argument and tries to render it as a
SQL value directly in the formatted SQL string rather
than turning it into a positional parameter:
* `nil` becomes `NULL`
* keywords and symbols become upper case entities (with `-` replaced by space)
* strings become inline SQL strings (with single quotes)
* a sequence has each element formatted inline and then joined with spaces
* all other values are just rendered via Clojure's `str` function

```clojure
(sql/format {:where [:= :x [:inline "foo"]]})
;;=> ["WHERE x = 'foo'"]
```

## interval

Accepts two arguments: an expression and a keyword (or a symbol)
that represents a time unit. Produces an `INTERVAL` expression:

```clojure
(sql/format-expr [:date_add [:now] [:interval 30 :days]])
;;=> ["DATE_ADD(NOW(), INTERVAL ? DAYS)" 30]
```

## lift

Used to wrap a Clojure value that should be passed as a
SQL parameter but would otherwise be treated as a SQL
expression or statement, i.e., a sequence or hash map.
This can be useful when dealing with JSON types:

```clojure
(sql/format {:where [:= :json-col [:lift {:a 1 :b "two"}]]})
;;=> ["WHERE json_col = ?" {:a 1 :b "two"}]
```

## nest

Used to wrap an expression when you want an extra
level of parentheses around it:

```clojure
(sql/format {:where [:= :x 42]})
;;=> ["WHERE x = ?" 42]
(sql/format {:where [:nest [:= :x 42]]})
;;=> ["WHERE (x = ?)" 42]
```

`nest` is also supported as a SQL clause for the same reason.

## not

Accepts a single expression and formats it with `NOT`
in front of it:

```clojure
(sql/format-expr [:not nil])
;;=> ["NOT NULL"]
(sql/format-expr [:not [:= :x 42]])
;;=> ["NOT x = ?" 42]
```

## param

Used to identify a named parameter in a SQL expression
as an alternative to a keyword (or a symbol) that begins
with `?`:

```clojure
(sql/format {:where [:= :x :?foo]} {:params {:foo 42}})
;;=> ["WHERE x = ?" 42]
(sql/format {:where [:= :x [:param :foo]]} {:params {:foo 42}})
;;=> ["WHERE x = ?" 42]
```

## raw

Accepts a single argument and renders it as literal SQL
in the formatted string:

```clojure
(sql/format {:select [:a [[:raw "@var := foo"]]]})
;;=> ["SELECT a, @var := foo"]
```

If the argument is a sequence of expressions, they
will each be rendered literally and joined together
(with no spaces):

```clojure
(sql/format {:select [:a [[:raw ["@var" " := " "foo"]]]]})
;;=> ["SELECT a, @var := foo"]
```

When a sequence of expressions is supplied, any
subexpressions that are, in turn, sequences will be
formatted as regular SQL expressions and that SQL
will be joined into the result, along with any
parameters from them:

```clojure
(sql/format {:select [:a [[:raw ["@var := " [:inline "foo"]]]]]})
;;=> ["SELECT a, @var := 'foo'"]
(sql/format {:select [:a [[:raw ["@var := " ["foo"]]]]]})
;;=> ["SELECT a, @var := ?" "foo"]
```
