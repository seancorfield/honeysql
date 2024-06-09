# SQL Special Syntax

This section lists the function-like expressions that
HoneySQL supports out of the box which are formatted
as special syntactic forms.

The first group are used for SQL expressions. The second (last group) are used primarily in column definitions (as part of `:with-columns` and `:add-column` / `:alter-column`).

The examples in this section assume the following:

```clojure
(require '[honey.sql :as sql])
```

## alias

Accepts a single argument which should be an alias name (from an `AS` clause
elsewhere in the overall SQL statement) and uses alias formatting rules rather
than table/column formatting rules (different handling of dots and hyphens).
This allows you to override HoneySQL's default assumption about entity names
and strings.

```clojure
(sql/format {:select [[:column-name "some-alias"]]
             :from :b
             :order-by [[[:alias "some-alias"]]]})
;;=> ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
(sql/format {:select [[:column-name :'some-alias]]
             :from :b
             :order-by [[[:alias :'some-alias]]]})
;;=> ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
```

## array

Accepts either an expression (that evaluates to a sequence) or a subquery
(hash map). In the expression case, also accepts an optional second argument
that specifies the type of the array.

Produces either an `ARRAY[..]` or an `ARRAY(subquery)` expression.

In the expression case, produces `ARRAY[?, ?, ..]` for the elements of that
sequence (as SQL parameters):

```clojure
(sql/format-expr [:array (range 5)])
;;=> ["ARRAY[?, ?, ?, ?, ?]" 0 1 2 3 4]
(sql/format-expr [:array (range 3) :text])
;;=> ["ARRAY[?, ?, ?]::TEXT[]" 0 1 2]
(sql/format-expr [:array [] :integer])
;;=> ["ARRAY[]::INTEGER[]"]
```

> Note: you cannot provide a named parameter as the argument for `:array` because the generated SQL depends on the number of elements in the sequence, so the following throws an exception:

<!-- :test-doc-blocks/skip -->
```clojure
(sql/format {:select [[[:array :?tags] :arr]]} {:params {:tags [1 2 3]}})
```

You can do the following instead:

```clojure
(let [tags [1 2 3]]
  (sql/format {:select [[[:array tags] :arr]]} {:inline true}))
;;=> ["SELECT ARRAY[1, 2, 3] AS arr"]
```

In addition, the argument to `:array` is treated as a literal sequence of Clojure values and is **not** interpreted as a HoneySQL expression, so you must use the `{:inline true}` formatting option as shown above rather than try to inline the values like this:

```clojure
(sql/format {:select [[[:array [:inline [1 2 3]]] :arr]]})
;;=> ["SELECT ARRAY[inline, (?, ?, ?)] AS arr" 1 2 3]
```

In the subquery case, produces `ARRAY(subquery)`:

```clojure
(sql/format {:select [[[:array {:select :* :from :table}] :arr]]})
;;=> ["SELECT ARRAY(SELECT * FROM table) AS arr"]
```

## at time zone

Accepts two arguments: an expression (assumed to be a date/time of some sort)
and a time zone name or identifier (can be a string, a symbol, or a keyword):

```clojure
(sql/format-expr [:at-time-zone [:now] :UTC])
;;=> ["NOW() AT TIME ZONE 'UTC'"]
```

The time zone name or identifier will be inlined (as a string) and therefore
cannot be an expression.

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
;; => ["CASE WHEN a < ? THEN ? WHEN a > ? THEN ? ELSE ? END" 10 "small" 100 "big" "medium"]
```

Google BigQuery supports a variant of `CASE` that takes an expression and then the `WHEN`
clauses contain expressions to match against, rather than conditions. HoneySQL supports
this using `:case-expr`:

```clojure
(sql/format-expr [:case-expr :a 10 "small" 100 "big" :else "medium"])
;; => ["CASE a WHEN ? THEN ? WHEN ? THEN ? ELSE ? END" 10 "small" 100 "big" "medium"]
```

## cast

A SQL `CAST` expression. Expects an expression and something
that produces a SQL type:

```clojure
(sql/format [:cast :a :int])
;;=> ["CAST(a AS INT)"]
```

Quoting does not affect the type in a `CAST`, only the expression:

```clojure
(sql/format [:cast :a :int] {:quoted true})
;;=> ["CAST(\"a\" AS INT)"]
```

A hyphen (`-`) in the type name becomes a space:

```clojure
(sql/format [:cast :a :double-precision])
;;=> ["CAST(a AS DOUBLE PRECISION)"]
```

If you want an underscore in the type name, you have two choices:

```clojure
(sql/format [:cast :a :some_type])
;;=> ["CAST(a AS SOME_TYPE)"]
```

or:

```clojure
(sql/format [:cast :a :'some-type])
;;=> ["CAST(a AS some_type)"]
```

> Note: In HoneySQL 2.4.947 and earlier, the type name was incorrectly affected by the quoting feature, and a hyphen in a type name was incorrectly changed to underscore. This was corrected in 2.4.962.

## composite

Accepts any number of expressions and produces a composite
expression (comma-separated, wrapped in parentheses):

```clojure
(sql/format-expr [:composite :a :b "red" [:+ :x 1]])
;;=> ["(a, b, ?, x + ?)" "red" 1]
```

This can be useful in a number of situations where you want a composite
value, as above, or a composite based on or declaring columns names:

```clojure
(sql/format {:select [[[:composite :a :b] :c]] :from :table})
;;=> ["SELECT (a, b) AS c FROM table"]
```

```clojure
(sql/format {:update :table :set {:a :v.a}
             :from [[{:values [[1 2 3]
                               [4 5 6]]}
                     [:v [:composite :a :b :c]]]]
             :where [:and [:= :x :v.b] [:> :y :v.c]]})
;;=> ["UPDATE table SET a = v.a FROM (VALUES (?, ?, ?), (?, ?, ?)) AS v (a, b, c) WHERE (x = v.b) AND (y > v.c)" 1 2 3 4 5 6]
```

## distinct

Accepts a single expression and prefixes it with `DISTINCT `:

```clojure
(sql/format {:select [ [[:count [:distinct :status]] :n] ] :from :table})
;;=> ["SELECT COUNT(DISTINCT status) AS n FROM table"]
```

## dot .

Accepts an expression and a field (or column) selection:

```clojure
(sql/format {:select [ [[:. :t :c]] [[:. :s :t :c]] ]})
;;=> ["SELECT t.c, s.t.c"]
```

Can be used with `:nest` for field selection from composites:

```clojure
(sql/format {:select [ [[:. [:nest :v] :*]] [[:. [:nest [:myfunc :x]] :y]] ]})
;;=> ["SELECT (v).*, (MYFUNC(x)).y"]
```

## entity

Accepts a single keyword or symbol argument and produces a
SQL entity. This is intended for use in contexts that would
otherwise produce a sequence of SQL keywords, such as when
constructing DDL statements.

<!-- :test-doc-blocks/skip -->
```clojure
[:tablespace :quux]
;;=> TABLESPACE QUUX
[:tablespace [:entity :quux]]
;;=> TABLESPACE quux
```

## escape

Intended to be used with regular expression patterns to
specify the escape characters (if any).

```clojure
(sql/format {:select :* :from :foo
             :where [:similar-to :foo [:escape "bar" [:inline  "*"]]]})
;;=> ["SELECT * FROM foo WHERE foo SIMILAR TO ? ESCAPE '*'" "bar"]
```

## filter, within-group

Used to produce PostgreSQL's `FILTER` and `WITHIN GROUP` expressions.
See also **order-by** below.

These both accept a SQL expression followed by a SQL clause.
Filter generally expects an aggregate expression and a `WHERE` clause.
Within group generally expects an aggregate expression and an `ORDER BY` clause.

```clojure
(sql/format {:select [:a :b [[:filter :%count.* {:where [:< :x 100]}] :c]
                     [[:within-group [:percentile_disc [:inline 0.25]]
                                     {:order-by [:a]}] :inter_max]
                     [[:within-group [:percentile_cont [:inline 0.25]]
                                     {:order-by [:a]}] :abs_max]]
             :from :aa}
             {:pretty true})
;;=> ["
SELECT a, b, COUNT(*) FILTER (WHERE x < ?) AS c, PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY a ASC) AS inter_max, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY a ASC) AS abs_max
FROM aa
"
100]
```

There are helpers for both `filter` and `within-group`. Be careful with `filter`
since it shadows `clojure.core/filter`:

```clojure
(refer-clojure :exclude '[filter])
(require '[honey.sql.helpers :refer [select filter within-group from order-by where]])

(sql/format (-> (select :a :b [(filter :%count.* (where :< :x 100)) :c]
                        [(within-group [:percentile_disc [:inline 0.25]]
                                       (order-by :a)) :inter_max]
                        [(within-group [:percentile_cont [:inline 0.25]]
                                       (order-by :a)) :abs_max])
                (from :aa))
                {:pretty true})
;;=> ["
SELECT a, b, COUNT(*) FILTER (WHERE x < ?) AS c, PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY a ASC) AS inter_max, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY a ASC) AS abs_max
FROM aa
"
100]
```

## ignore/respect nulls

Both of these accept a single argument -- an expression -- and
renders that expression followed by `IGNORE NULLS` or `RESPECT NULLS`:

```clojure
(sql/format-expr [:array_agg [:ignore-nulls :a]])
;;=> ["ARRAY_AGG(a IGNORE NULLS)"]
(sql/format-expr [:array_agg [:respect-nulls :a]])
;;=> ["ARRAY_AGG(a RESPECT NULLS)"]
```

## inline

Accepts one or more arguments and tries to render them as a
SQL values directly in the formatted SQL string rather
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

If multiple arguments are provided, they are individually formatted as above
and joined into a single SQL string with spaces:

```clojure
(sql/format {:where [:= :x [:inline :DATE "2019-01-01"]]})
;;=> ["WHERE x = DATE '2019-01-01'"]
```

This is convenient for rendering DATE/TIME/TIMESTAMP literals in SQL.

If an argument is an expression, it is formatted as a regular SQL expression
except that any parameters are inlined:

```clojure
(sql/format {:where [:= :x [:inline [:date_add [:now] [:interval 30 :days]]]]})
;;=> ["WHERE x = DATE_ADD(NOW(), INTERVAL 30 DAYS)"]
```

In particular, that means that you can use `:inline` to inline a parameter
value:

```clojure
(sql/format {:where [:= :x [:inline :?foo]]} {:params {:foo "bar"}})
;;=> ["WHERE x = 'bar'"]
(sql/format {:where [:= :x [:inline [:param :foo]]]} {:params {:foo "bar"}})
;;=> ["WHERE x = 'bar'"]
```

## interval

Accepts one or two arguments: either a string or an expression and
a keyword (or a symbol) that represents a time unit.
Produces an `INTERVAL` expression:

```clojure
(sql/format-expr [:date_add [:now] [:interval 30 :days]])
;;=> ["DATE_ADD(NOW(), INTERVAL ? DAYS)" 30]
(sql/format-expr [:date_add [:now] [:interval "24 Hours"]])
;;=> ["DATE_ADD(NOW(), INTERVAL '24 Hours')"]
```

> Note: PostgreSQL also has an `INTERVAL` data type which is unrelated to this syntax. In PostgreSQL, the closet equivalent would be `[:cast "30 days" :interval]` which will lift `"30 days"` out as a parameter. In DDL, for PostgreSQL, you can use `:interval` to produce the `INTERVAL` data type (without wrapping it in a vector).

## join

Accepts a table name (or expression) followed by one or more join clauses.
Produces a nested `JOIN` expression, typically used as the table expression of
a `JOIN` clause.

```clojure
(sql/format {:join [[[:join :tbl1 {:left-join [:tbl2 [:using :id]]}]]]})
;;=> ["INNER JOIN (tbl1 LEFT JOIN tbl2 USING (id))"]
```

An alias can be provided:

```clojure
(sql/format {:join [[[:join [:tbl1 :t] {:left-join [:tbl2 [:using :id]]}]]]})
;;=> ["INNER JOIN (tbl1 AS t LEFT JOIN tbl2 USING (id))"]
```

To provide an expression, an extra level of `[...]` is needed:

```clojure
(sql/format {:join [[[:join [[:make_thing 42] :t] {:left-join [:tbl2 [:using :id]]}]]]})
;;=> ["INNER JOIN (MAKE_THING(?) AS t LEFT JOIN tbl2 USING (id))" 42]
```

## lateral

Accepts a single argument that can be a (`SELECT`) clause or
a (function call) expression. Produces a `LATERAL` subquery
clause based on the `SELECT` clause or the SQL expression.

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

`:nest` is also supported as a SQL clause for the same reason.

```clojure
;; BigQuery requires UNION clauses be parenthesized:
(sql/format {:union-all [{:nest {:select :*}} {:nest {:select :*}}]})
;;=> ["(SELECT *) UNION ALL (SELECT *)"]
```

## not

Accepts a single expression and formats it with `NOT`
in front of it:

```clojure
(sql/format-expr [:not nil])
;;=> ["NOT NULL"]
(sql/format-expr [:not [:= :x 42]])
;;=> ["NOT (x = ?)" 42]
```

## order-by

In addition to the `ORDER BY` clause, HoneySQL also supports `ORDER BY`
in an expression (for PostgreSQL). It accepts a SQL expression followed
by an ordering specifier, which can be an expression or a pair of expression
and direction (`:asc` or `:desc`):

```clojure
(sql/format {:select [[[:array_agg [:order-by :a [:b :desc]]]]] :from :table})
;;=> ["SELECT ARRAY_AGG(a ORDER BY b DESC) FROM table"]
(sql/format (-> (select [[:array_agg [:order-by :a [:b :desc]]]])
                (from :table)))
;;=> ["SELECT ARRAY_AGG(a ORDER BY b DESC) FROM table"]
(sql/format {:select [[[:string_agg :a [:order-by [:inline ","] :a]]]] :from :table})
;;=> ["SELECT STRING_AGG(a, ',' ORDER BY a ASC) FROM table"]
(sql/format (-> (select [[:string_agg :a [:order-by [:inline ","] :a]]])
                (from :table)))
;;=> ["SELECT STRING_AGG(a, ',' ORDER BY a ASC) FROM table"]
```

There is no helper for the `ORDER BY` special syntax: the `order-by` helper
only produces a SQL clause.

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
;;=> ["SELECT a, @var := (?)" "foo"]
;; when multiple expressions are provided, the enclosing
;; vector can be omitted:
(sql/format {:select [:a [[:raw "@var := " [:inline "foo"]]]]})
;;=> ["SELECT a, @var := 'foo'"]
(sql/format {:select [:a [[:raw "@var := " ["foo"]]]]})
;;=> ["SELECT a, @var := (?)" "foo"]
```

`:raw` is also supported as a SQL clause for the same reason.

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

<!-- :test-doc-blocks/skip -->
```clojure
[:foreign-key] ;=> FOREIGN KEY
[:primary-key] ;=> PRIMARY KEY
```

Otherwise, these render as regular function calls:

<!-- :test-doc-blocks/skip -->
```clojure
[:foreign-key :a]    ;=> FOREIGN KEY(a)
[:primary-key :x :y] ;=> PRIMARY KEY(x, y)
```

### constraint, default, references

Although these are grouped together, they are generally
used differently. This group renders as SQL keywords if
no arguments are provided. If a single argument is
provided, this renders as a SQL keyword followed by the
argument. If two or more arguments are provided, this
renders as a SQL keyword followed by the first argument,
followed by the rest as a regular argument list:

<!-- :test-doc-blocks/skip -->
```clojure
[:default]              ;=> DEFAULT
[:default 42]           ;=> DEFAULT 42
[:default "str"]        ;=> DEFAULT 'str'
[:constraint :name]     ;=> CONSTRAINT name
[:references :foo :bar] ;=> REFERENCES foo(bar)
```

### index, unique

These behave like the group above except that if the
first argument is `nil`, it is omitted:

<!-- :test-doc-blocks/skip -->
```clojure
[:index :foo :bar :quux] ;=> INDEX foo(bar, quux)
[:index nil :bar :quux]  ;=> INDEX(bar, quux)
[:unique :a :b]          ;=> UNIQUE a(b)
```
