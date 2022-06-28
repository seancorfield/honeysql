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
* `:params` -- a hash map providing values for named parameters, identified by names (keywords or symbols) that start with `?` in the DSL; the default is that any such named parameters will have `nil` values,
* `:quoted` -- a Boolean indicating whether or not to quote (strop) identifiers (table and column names); the default is `false` -- identifiers are not quoted,
* `:quoted-snake` -- a Boolean indicating whether or not quoted and string identifiers should have `-` replaced by `_`; the default is `false` -- quoted and string identifiers are left exactly as-is,
* `:values-default-columns` -- a sequence of column names that should have `DEFAULT` values instead of `NULL` values if used in a `VALUES` clause with no associated matching value in the hash maps passed in; the default behavior is for such missing columns to be given `NULL` values.

See below for the interaction between `:dialect` and `:quoted`.

## `:cache`

See the [**Caching** section of the **General Reference**](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started/general-reference#caching)
for details.

Added in 2.2.858.

## `:checking`

The `:checking` option defaults to `:none`. If `:checking :basic` is
specified, certain obvious errors -- such as `IN` with an empty collection
or `SELECT` with an empty list of columns --
are treated as an error and an exception is thrown. If `:checking :strict`
is specified, certain dubious constructs -- such as `IN` with a collection
containing `NULL` values -- are also treated as an error and an exception is
thrown. It is expected that this feature will be expanded over time
to help avoid generating illegal SQL.

## `:dialect`

## `:inline`

The `:inline` option suppresses the generation of parameters in
the SQL string and instead tries to inline all the values directly
into the SQL string. The behavior is as if each value in the DSL
was wrapped in `[:inline `..`]`:

* `nil` becomes the SQL value `NULL`,
* Clojure strings become inline SQL strings with single quotes (so `"foo"` becomes `'foo'`),
* keywords and symbols become SQL keywords (uppercase, with `-` replaced by a space),
* everything else is just turned into a string (by calling `str`) and added to the SQL string.

## `:params`

The `:params` option is used to specify
the values of named parameters in the DSL.

## `:quoted`

## `:quoted-snake`

## `:values-default-columns`

Added in 2.1.818.
