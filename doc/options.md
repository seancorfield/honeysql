# All the Options

`format` accepts options as either a single hash map argument or
as named arguments (alternating keys and values). If you are using
Clojure 1.11 (or later) you can mix'n'match, providing some options
as named arguments followed by other options in a hash map.

[**Getting Started**](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started)
talked about the `:dialect`, `:params`, and `:quoted` options,
but `format` accepts a number of other options that control
how the data structure DSL is converted to a SQL string
and the associated parameters.

## Format Options

All options may be omitted. The default behavior of each option is described in the following list, with expanded details of each option in the sections that follow.

* `:cache` -- an atom containing a [clojure.core.cache](https://github.com/clojure/core.cache) cache used to cache generated SQL; the default behavior is to generate SQL on each call to `format`,
* `:checking` -- `:none` (default), `:basic`, or `:strict` to control the amount of lint-like checking that HoneySQL performs,
* `:dialect` -- a keyword that identifies a dialect to be used for this specific call to `format`; the default is to use what was specified in `set-dialect!` or `:ansi` if no other dialect has been set,
* `:inline` -- a Boolean indicating whether or not to inline parameter values, rather than use `?` placeholders and a sequence of parameter values; the default is `false` -- values are not inlined,
* `:numbered` -- a Boolean indicating whether to generate numbered placeholders in the generated SQL (`$1`, `$2`, etc) or positional placeholders (`?`); the default is `false` (positional placeholders); this option was added in 2.4.962,
* `:params` -- a hash map providing values for named parameters, identified by names (keywords or symbols) that start with `?` in the DSL; the default is that any such named parameters will have `nil` values,
* `:quoted` -- a Boolean indicating whether or not to quote (strop) SQL entity names (table and column names); the default is `nil` -- alphanumeric SQL entity names are not quoted but (as of 2.3.928) "unusual" SQL entity names are quoted; a `false` value turns off all quoting,
* `:quoted-snake` -- a Boolean indicating whether or not quoted and string SQL entity names should have `-` replaced by `_`; the default is `false` -- quoted and string SQL entity names are left exactly as-is,
* `:values-default-columns` -- a sequence of column names that should have `DEFAULT` values instead of `NULL` values if used in a `VALUES` clause with no associated matching value in the hash maps passed in; the default behavior is for such missing columns to be given `NULL` values.

As of 2.4.947, you can call `set-options!` with an options hash map to change the
global defaults of certain options:

* `:checking` -- can be `:basic` or `:strict`; specify `:none` to reset to the default,
* `:inline` -- can be `true` but consider the security issues this causes by not using parameterized SQL statements; specify `false` (or `nil`) to reset to the default,
* `:numbered` -- can be `true` or `false`; specify `false` to reset to the default,
* `:quoted` -- can be `true` or `false`; specify `nil` to reset to the default; calling `set-dialect!` or providing a `:dialect` option to `format` will override the global default,
* `:quoted-snake` -- can be `true`; specify `false` (or `nil`) to reset to the default.

Other options may only be specified directly in calls to `format` as they are considered
per-statement, rather than global.

See below for the interaction between `:dialect` and `:quoted`.

## `:cache`

Providing a `:cache` option -- an atom containing a `core.cache` style cache data structure -- causes `format` to try to cache the
generated SQL string, based on the value of the DSL data structure.
When you use `:cache`, you should generally use named parameters
(names that start with `?`) instead of regular values.

See the [**Caching** section of the **General Reference**](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started/general-reference#caching)
for details.

> Note: you cannot use named parameters with `:in` when using `:cache` because `:in` "unrolls" the parameter and that will break the cache lookup rules.

Added in 2.2.858.

## `:checking`

The `:checking` option defaults to `:none`.
If `:checking :basic` is specified, certain obvious errors
are treated as an error and an exception is thrown.
If `:checking :strict` is specified, certain dubious constructs are also treated as an error and an exception is
thrown.
It is expected that this feature will be expanded over time
to help avoid generating illegal SQL.

Here are the checks for each level:
* `:basic` -- `DELETE` and `DELETE FROM` without a `WHERE` clause; `IN` with an empty collection; `SELECT` with an empty list of columns; `UPDATE` without a `WHERE` clause.
* `:strict` -- (all the `:basic` checks plus) `IN` with a collection containing `NULL` values (since this will not match rows).

## `:dialect`

If `:dialect` is provided, `:quoted` will default to `true` for this call. You can still specify `:quoted false` to turn that back off.

Valid dialects are:

* `:ansi`
* `:mysql`
* `:oracle`
* `:sqlserver`

New dialects can be created with the `register-dialect!` call.

By default, `:ansi` is the dialect used. `set-dialect!` can
set a different default dialect. The `:dialect` option only affects
the current call to `format`.

## `:inline`

The `:inline` option suppresses the generation of parameters in
the SQL string and instead tries to inline all the values directly
into the SQL string. The behavior is as if each value in the DSL
was wrapped in `[:inline `..`]`:

* `nil` becomes the SQL value `NULL`,
* Clojure strings become inline SQL strings with single quotes (so `"foo"` becomes `'foo'`),
* keywords and symbols become SQL keywords (uppercase, with `-` replaced by a space),
* everything else is just turned into a string (by calling `str`) and added to the SQL string.

> Note: you can provide additional inline formatting by extending the `InlineValue` protocol from `honey.sql.protocols` to new types.

## `:numbered`

By default, HoneySQL generates SQL using positional placeholders (`?`).
Specifying `:numbered true` tells HoneySQL to generate SQL using
numbered placeholders instead (`$1`, `$2`, etc). This can be set
globally using `set-options!`.

## `:params`

The `:params` option provides a mapping from named parameters
to values for this call to `format`. For example:

```clojure
(require '[honey.sql :as sql])

(-> {:select :* :from :table :where [:= :id :?id]}
    (sql/format {:params {:id 42}}))
;;=> ["SELECT * FROM table WHERE id = ?" 42]
(-> '{select * from table where (= id ?id)}
    (sql/format {:params {:id 42}}))
;;=> ["SELECT * FROM table WHERE id = ?" 42]
```

## `:quoted`

If `:quoted true`, or `:dialect` is provided (and `:quoted` is not
specified as `false`), SQL entity names that represent
tables and columns will be quoted (stropped) according to the
selected dialect.

If `:quoted false`, SQL entity names that represent tables and columns
will not be quoted. If those SQL entity names are reserved words in
SQL, the generated SQL will be invalid.

The quoting (stropping) is dialect-dependent:
* `:ansi` -- uses double quotes
* `:mysql` -- uses backticks
* `:oracle` -- uses double quotes
* `:sqlserver` -- user square brackets

As of 2.3.928, if `:quoted` and `:dialect` are not provided, and no
default quoting strategy has been specified (via `set-dialect!`) then
alphanumeric SQL entity names will not be quoted but "unusual" SQL entity names will

## `:quoted-snake`

Where strings are used to identify table or column names, they are
treated as-is. If `:quoted true` (or a `:dialect` is specified),
those SQL entity names are quoted as-is.

Where keywords or symbols are used to identify table or column
names, and `:quoted true` is provided, those SQL entity names are
quoted as-is.

If `:quoted-snake true` is provided, those SQL entity names are quoted
but any `-` in them are replaced by `_` -- that replacement is the
default in unquoted SQL entity names.

This allows quoting to be used but still maintain the Clojure
(kebab case) to SQL (snake case) mappings.

## `:values-default-columns`

This option determines the behavior of the `:values` clause, when
column values are missing from one or more of the hash maps passed
in.

By default, missing column values are replaced with `NULL` in the
generated SQL. `:values-default-columns` can specify a set of
column names that should instead be given the value `DEFAULT` if
their column value is missing from one or more hash maps.

That in turn should cause their declared default value to be used
(from the column definition in the table) and is useful for
situations where `NULL` is not an appropriate default for a missing
column value.

Added in 2.1.818.
