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

## = <> < > <= >=

Binary comparison operators. These expect exactly
two arguments.

`not=` and `!=` are accepted as aliases for `<>`.

## is, is-not

Binary predicates for `NULL` and Boolean values:

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

Mathematical and bitwise operators. `+` and `*` are
variadic; the rest are strictly binary operators.

## like, not like, ilike, not ilike, regexp

Pattern matching binary operators. `regex` is accepted
as an alias for `regexp`.

`similar-to` and `not-similar-to` are also supported.

## ||

Variadic string concatenation operator.
