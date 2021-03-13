# SQL Special Syntax

This section lists the function-like expressions that
HoneySQL supports out of the box which are formatted
as special syntactic forms.

The first group are used for SQL expressions. The second (last group) are used primarily in column definitions (as part of `:with-columns` and `:add-column` / `:modify-column`).

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

## entity

Accepts a single keyword or symbol argument and produces a
SQL entity. This is intended for use in contexts that would
otherwise produce a sequence of SQL keywords, such as when
constructing DDL statements.

## escape

Intended to be used with regular expression patterns to
specify the escape characters (if any).

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

## lateral

Accepts a single argument that can be a (`SELECT`) clause or
a (function call) expression.

## lift

Used to wrap a Clojure value that should be passed as a
SQL parameter but would otherwise be treated as a SQL
expression or statement, i.e., a sequence or hash map.
This can be useful when dealing with JSON types:

```clojure
(sql/format {:where [:= :json-col [:lift {:a 1 :b "two"}]]})
;;=> ["WHERE json_col = ?" {:a 1 :b "two"}]
```

> Note: HoneySQL 1.x used `honeysql.format/value` for this.

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

## over

This is intended to be used with the `:window` and `:partition-by` clauses.

`:over` takes any number of window expressions which are either pairs or triples
that have an aggregation expression, a window function, and an optional alias.

The window function may either be a SQL entity (named in a `:window` clause)
or a SQL clause that describes the window (e.g., using `:partition-by` and/or `:order-by`).

Since a function call (using `:over`) needs to be wrapped in a sequence for a
`:select` clause, it is usually easier to use the `over` helper function
to construct this expression.

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

## Column Descriptors

There are three types of descriptors that vary
in how they treat their first argument. All three
descriptors automatically try to inline any parameters
(and will throw an exception if they can't, since these
descriptors are meant to be used in column or index
specifications).

### foreign-key, primary-key

If no arguments are provided, these render as just SQL
keywords (uppercase):

```clojure
[:foreign-key] ;=> FOREIGN KEY
[:primary-key] ;=> PRIMARY KEY
```

Otherwise, these render as regular function calls:

```clojure
[:foreign-key :a]    ;=> FOREIGN KEY(a)
[:primary-key :x :y] ;=> PRIMARY KEY(x, y)
```

## constraint, default, references

Although these are grouped together, they are generally
used differently. This group renders as SQL keywords if
no arguments are provided. If a single argument is
provided, this renders as a SQL keyword followed by the
argument. If two or more arguments are provided, this
renders as a SQL keyword followed by the first argument,
followed by the rest as a regular argument list:

```clojure
[:default]              ;=> DEFAULT
[:default 42]           ;=> DEFAULT 42
[:default "str"]        ;=> DEFAULT 'str'
[:constraint :name]     ;=> CONSTRAINT name
[:references :foo :bar] ;=> REFERENCES foo(bar)
```

## index, unique

These behave like the group above except that if the
first argument is `nil`, it is omitted:

```clojure
[:index :foo :bar :quux] ;=> INDEX foo(bar, quux)
[:index nil :bar :quux]  ;=> INDEX(bar, quux)
[:unique :a :b]          ;=> UNIQUE a(b)
```
