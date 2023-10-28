# Extending HoneySQL

Out of the box, HoneySQL supports most standard ANSI SQL clauses
and expressions but where it doesn't support something you need
you can add new clauses, new operators, and new "functions" (or
"special syntax").

There are three extension points in `honey.sql` that let you
register formatters or behavior corresponding to clauses,
operators, and functions.

Built in clauses include: `:select`, `:from`, `:where` and
many more. Built in operators include: `:=`, `:+`, `:mod`.
Built in functions (special syntax) include: `:array`, `:case`,
`:cast`, `:inline`, `:raw` and many more.

See also the section on
[database-specific hints and tips](databases.md), which may
let you avoid extending HoneySQL.

## Extending what `:inline` can do

By default, the `:inline` option can convert a fairly
basic set of values/types to SQL strings:
* `nil`
* strings
* keywords and symbols
* vectors
* UUIDs (Clojure only)

Everything is naively converted by calling `str`.

You can extend `honey.sql.protocols/InlineValue` to
other types and defining how the `sqlize` function
should behave. It takes a single argument, the value
to be inlined (converted to a SQL string).

## Registering a New Clause Formatter

`honey.sql/register-clause!` accepts a keyword (or a symbol)
that should be treated as a new clause in a SQL statement,
a "formatter", and a keyword (or a symbol) that identifies
an existing clause that this new one should be ordered before.

The formatter can either be a function
of two arguments or a previously registered clause (so
that you can easily reuse formatters).

The formatter function will be called with:
* The clause name (always as a keyword),
* The sequence of arguments provided.

The third argument to `register-clause!` allows you to
insert your new clause formatter so that clauses are
formatted in the correct order for your SQL dialect.
For example, `:select` comes before `:from` which comes
before `:where`. You can call `clause-order` to see what the
current ordering of clauses is.

> Note: if you call `register-clause!` more than once for the same clause, the last call "wins". This allows you to correct an incorrect clause order insertion by simply calling `register-clause!` again with a different third argument.

## Defining a Helper Function for a New Clause

Having registered a new clause, you might also want a helper function
for it, just as the built-in clauses have helpers in `honey.sql.helpers`.
Two functions exist in that namespace to make it easier for you to
define your own helpers:

* `generic-helper-variadic` -- most clauses accept an arbitrary number of items in a sequence and multiple calls in a DSL expression will merge so this is the helper you will use for most clauses,
* `generic-helper-unary` -- a handful of clauses only accept a single item and cannot be merged (they behave as "last one wins"), so this helper supports that semantic.

Each of these helper support functions should be called with the keyword that
identifies your new clause and the sequence of arguments passed to it. See
the docstrings for more detail.

You might have:

<!-- :test-doc-blocks/skip -->
```clojure
(sql/register-clause! :my-clause my-formatter :where)
(defn my-clause [& args] (h/generic-helper-variadic :my-clause args))
```

## Registering a New Operator

`honey.sql/register-op!` accepts a keyword (or a symbol) that
should be treated as a new infix operator.

All operators are treated as variadic and an exception will be
thrown if they are provided no arguments:

```clojure
(require '[honey.sql :as sql])

(sql/register-op! :<=>)
;; and then use the new operator:
(sql/format {:select [:*], :from [:table], :where [:<=> 13 :x 42]})
;; will produce:
;;=> ["SELECT * FROM table WHERE ? <=> x <=> ?" 13 42]
```

If you are building expressions programmatically, you
may want your new operator to ignore "empty" expressions,
i.e., where your expression-building code might produce
`nil`. The built-in operators `:and` and `:or` ignore
such `nil` expressions. You can specify `:ignore-nil true`
to achieve that:

```clojure
(sql/register-op! :<=> :ignore-nil true)
;; and then use the new operator:
(sql/format {:select [:*], :from [:table], :where [:<=> nil :x 42]})
;; will produce:
;;=> ["SELECT * FROM table WHERE x <=> ?" 42]
```

### PostgreSQL Operators

A number of PostgreSQL operators contain `@` which is not legal in a Clojure keyword or symbol (as literal syntax). The recommendation is to `def` your own name for these
operators, using `at` in place of `@`, with an explicit call to `keyword` (or `symbol`), and then use that `def`'d name when registering new operators and when writing
your DSL expressions:

```clojure
(def <at (keyword "<@"))
(sql/register-op! <at)
;; and use it in expressions: [<at :submitted [:range :begin :end]]
```

## Registering a New Function (Special Syntax)

`honey.sql/register-fn!` accepts a keyword (or a symbol)
that should be treated as new syntax (as a function call),
and a "formatter". The formatter can either be a function
of two arguments or a previously registered "function" (so
that you can easily reuse formatters).

The formatter function will be called with:
* The function name (always as a keyword),
* The sequence of arguments provided.

For example:

<!-- :test-doc-blocks/skip -->
```clojure
(sql/register-fn! :foo (fn [f args] ..))

(sql/format {:select [:*], :from [:table], :where [:foo 1 2 3]})
```

Your formatter function will be called with `:foo` and `(1 2 3)`.
It should return a vector containing a SQL string followed by
any parameters:

```clojure
(sql/register-fn! :foo (fn [f args] ["FOO(?)" (first args)]))

(sql/format {:select [:*], :from [:table], :where [:foo 1 2 3]})
;; produces:
;;=> ["SELECT * FROM table WHERE FOO(?)" 1]
```

In practice, it is likely that your formatter would call
`sql/sql-kw` on the function name to produce a SQL representation
of it and would call `sql/format-expr` on each argument:

```clojure
(defn- foo-formatter [f [x]]
  (let [[sql & params] (sql/format-expr x)]
    (into [(str (sql/sql-kw f) "(" sql ")")] params)))

(sql/register-fn! :foo foo-formatter)

(sql/format {:select [:*], :from [:table], :where [:foo [:+ :a 1]]})
;; produces:
;;=> ["SELECT * FROM table WHERE FOO(a + ?)" 1]
```

## Registering a new Dialect

_New in HoneySQL 2.3.x_

The built-in dialects that HoneySQL supports are:
* `:ansi` -- the default, that quotes SQL entity names with double-quotes, like `"this"`
* `:mysql` -- quotes SQL entity names with backticks, and changes the precedence of `SET` in `UPDATE`
* `:nrql` -- as of 2.5.1091, see [New Relic NRQL Support](nrsql.md) for more details of the NRQL dialect
* `:oracle` -- quotes SQL entity names like `:ansi`, and does not use `AS` in aliases
* `:sqlserver` -- quotes SQL entity names with brackets, like `[this]`

A dialect spec is a hash map containing at least `:quote` but also optionally `:clause-order-fn` and/or `:as`:
* `:quote` -- a unary function that takes a string and returns the quoted version of it
* `:clause-order-fn` -- a unary function that takes a sequence of clause names (keywords) and returns an updated sequence of clause names; this defines the precedence of clauses in the DSL parser
* `:as` -- a boolean that indicates whether `AS` should be present in aliases (the default, if `:as` is omitted) or not (by specifying `:as false`)

To make writing new dialects easier, the following helper functions in `honey.sql` are available:
* `add-clause-before` -- a function that accepts the sequence of clause names, the (new) clause to add, and the clause to add it before (`nil` means add at the end)
* `get-dialect` -- a function that accepts an existing dialect name (keyword) and returns its spec (hash map)
* `strop` -- a function that accepts an opening quote, a string, and a closing quote and returns the quoted string, doubling-up any closing quote characters inside the string to make it legal SQL
* `upper-case` -- a locale-insensitive version of `clojure.string/upper-case`

For example, to add a variant of the `:ansi` dialect that forces names to be upper-case as well as double-quoting them:

```clojure
(sql/register-dialect! ::ANSI (update (sql/get-dialect :ansi) :quote comp sql/upper-case))
;; or you could do this:
(sql/register-dialect! ::ANSI {:quote #(sql/strop \" (sql/upper-case %) \")})

(sql/format {:select :foo :from :bar} {:dialect :ansi})
;;=> ["SELECT \"foo\" FROM \"bar\""]

(sql/format {:select :foo :from :bar} {:dialect ::ANSI})
;;=> ["SELECT \"FOO\" FROM \"BAR\""]
```
