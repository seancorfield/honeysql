# SQL Operators Supported

This section lists the operators that HoneySQL supports
out of the box. There is no operator precedence assumed
because SQL expressions are represented in prefix form,
just like Clojure expressions.

Operators can be specified as keywords or symbols. Use
`-` in the operator where the formatted SQL would have
a space (e.g., `:not-like` is formatted as `NOT LIKE`).

## and, or

Boolean operators. May take any number of expressions
as arguments. `nil` expressions are ignored which can
make it easier to programmatically build conditional
expressions (since an expression that should be omitted
can simply evaluate to `nil` instead).

```clojure
{...
 :where [:and [:= :type "match"]
              (when need-status [:in :status [1 5]])]
 ...}
;; if need-status is truthy:
;;=> ["...WHERE (type = ?) AND (status IN (?, ?))..." "match" 1 5]
;; or, if need-status is falsey:
;;=> ["...WHERE (type = ?)..." "match"]
{...
 :where [:or [:= :id 42] [:= :type "match"]]
 ...}
;;=> ["...WHERE (id = ?) OR (type = ?)..." 42 "match"]
```

## in

Predicate for checking an expression is
is a member of a specified set of values.

The two most common forms are:

* `[:in :col [val1 val2 ...]]` where the `valN` can be arbitrary expressions,
* `[:in :col {:select ...}]` where the `SELECT` specifies a single column.

`:col` could be an arbitrary SQL expression (but is most
commonly just a column name).

The former produces an inline vector expression with the
values resolved as regular SQL expressions (i.e., with
literal values lifted out as parameters): `col IN [?, ?, ...]`

The latter produces a sub-select, as expected: `col IN (SELECT ...)`

You can also specify the set of values via a named parameter:

* `[:in :col :?values]` where `:params {:values [1 2 ...]}` is provided to `format` in the options.

In this case, the named parameter is expanded directly when
`:in` is formatted to obtain the sequence of values (which
must be _sequential_, not a Clojure set). That means you
cannot use this approach and also specify `:cache` -- see
[cache in All the Options](options.md#cache) for more details.

Another supported form is checking whether a tuple is in
a selected set of values that specifies a matching number
of columns, producing `(col1, col2) IN (SELECT ...)`, but
you need to specify the columns (or expressions) using the
`:composite` special syntax:

* `[:in [:composite :col1 :col2] ...]`

This produces `(col1, col2) IN ...`

> Note: This is a change from HoneySQL 1.x which accepted a sequence of column names but required more work for arbitrary expressions.

## = <>

Binary comparison operators. These expect exactly
two arguments.

`not=` and `!=` are accepted as aliases for `<>`.

## < > <= >=

Comparison operators. These expect exactly
two arguments.

## is, is-not

Predicates for `NULL` and Boolean values:

```clojure
{...
 :where [:is :id nil]
 ...}
;;=> ["...WHERE col IS NULL..."]
{...
 :where [:is-not :id nil]
 ...}
;;=> ["...WHERE col IS NOT NULL..."]
{...
 :where [:is :col true]
 ...}
;;=> ["...WHERE col IS TRUE..."]
{...
 ;; unlike [:<> :col false], the following will include NULLs:
 :where [:is-not :col false]
 ...}
;;=> ["...WHERE col IS NOT FALSE..."]
```

## mod, xor, + - * / % | & ^

Mathematical and bitwise operators.

## like, not like, ilike, not ilike, regexp

Pattern matching operators. `regex` is accepted
as an alias for `regexp`.

`similar-to` and `not-similar-to` are also supported.

## ||

String concatenation operator.
