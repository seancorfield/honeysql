# Differences Between 1.x and 2.x

The goal of HoneySQL 1.x and earlier was to provide a DSL for vendor-neutral SQL, with the assumption that other libraries would provide the vendor-specific extensions to HoneySQL. HoneySQL 1.x's extension mechanism required quite a bit of internal knowledge (clause priorities and multiple multimethod extension points). It also used a number of custom record types, protocols, and data readers to provide various "escape hatches" in the DSL for representing arrays, function calls (in some situations), inlined values, parameters, and raw SQL, which led to a number of inconsistencies over time, as well as making some things very hard to express while other similar things were easy to express. Addressing bugs caused by vendor-specific differences and by some quirks of how SQL was generated gradually became harder and harder.

The goal of HoneySQL 2.x is to provide an easily-extensible DSL for SQL, supporting vendor-specific differences and extensions, that is as consistent as possible. A secondary goal is to make maintenance much easier by streamlining the machinery and reducing the number of different ways to write and/or extend the DSL.

The DSL itself -- the data structures that both versions convert to SQL and parameters via the `format` function -- is almost exactly the same between the two versions so that migration is relatively painless. The primary API -- the `format` function -- is preserved in 2.x, although the variadic options from 1.x have changed to an options hash map in 2.x as this is generally considered more idiomatic. See the **Option Changes** section below for the differences in the options supported.

HoneySQL 1.x supported Clojure 1.7 and later. HoneySQL 2.x requires Clojure 1.9 or later.

## Group, Artifact, and Namespaces

HoneySQL 2.x uses the group ID `com.github.seancorfield` with the original artifact ID of `honeysql`, in line with the recommendations in Inside Clojure's post about the changes in the Clojure CLI: [Deprecated unqualified lib names](https://insideclojure.org/2020/07/28/clj-exec/).

In addition, HoneySQL 2.x contains different namespaces so you can have both versions on your classpath without introducing any conflicts. The primary API is now in `honey.sql` and the helpers are in `honey.sql.helpers`. A Spec for the DSL data structure will be available in `honey.specs` at some point (work in progress).

### HoneySQL 1.x

```clojure
;; in deps.edn:
honeysql {:mvn/version "1.0.461"}
;; or, more correctly:
honeysql/honeysql {:mvn/version "1.0.461"}

;; in use:
(ns my.project
  (:require [honeysql.core :as sql]))

...
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]})
  ;;=> ["SELECT * FROM table WHERE id = ?" 1]
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]} :quoting :mysql)
  ;;=> ["SELECT * FROM `table` WHERE `id` = ?" 1]
```

The namespaces were:
* `honeysql.core` -- the primary API (`format`, etc),
* `honeysql.format` -- the logic for the formatting engine,
* `honeysql.helpers` -- helper functions to build the DSL,
* `honeysql.types` -- records, protocols, and data readers,
* `honeysql.util` -- internal utilities (macros).

Supported Clojure versions: 1.7 and later.

### HoneySQL 2.x

```clojure
;; in deps.edn:
com.github.seancorfield/honeysql {:mvn/version "2.x"}

;; in use:
(ns my.project
  (:require [honey.sql :as sql]))

...
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]})
  ;;=> ["SELECT * FROM table WHERE id = ?" 1]
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})
  ;;=> ["SELECT * FROM `table` WHERE `id` = ?" 1]
```

The new namespaces are:
* `honey.sql` -- the primary API (just `format` now),
* `honey.sql.helpers` -- helper functions to build the DSL,
* `honey.specs` -- a description of the DSL using `clojure.spec.alpha`.

Supported Clojure versions: 1.9 and later.

## API Changes

The primary API is just `honey.sql/format`. The `array`, `call`, `inline`, `param`, and `raw` functions have all become standard syntax in the DSL as functions (and their tagged literal equivalents have also gone away because they are no longer needed).

Other `honeysql.core` functions that no longer exist include: `build`, `qualify`, and `quote-identifier`. Many other public functions were essentially undocumented (neither mentioned in the README nor in the tests) and also no longer exist.

You can now select a non-ANSI dialect of SQL using the new `honey.sql/set-dialect!` function (which sets a default dialect for all `format` operations) or by passing the new `:dialect` option to the `format` function. `:ansi` is the default dialect (which will mostly incorporate PostgreSQL usage over time). Other dialects supported are `:mysql` (which has a different quoting strategy and uses a different ranking for the `:set` clause), `:oracle` (which is essentially the `:ansi` dialect but will control other things over time), and `:sqlserver` (which is essentially the `:ansi` dialect but with a different quoting strategy). Other dialects and changes may be added over time.

> Note: in general, all clauses are available in all dialects in HoneySQL unless the syntax of the clauses conflict between dialects (currently, no such clauses exist). The `:mysql` dialect is the only one so far that changes the priority ordering of a few clauses.

## Option Changes

As noted above, the variadic options for `format` have been replaced by a single hash map as the optional second argument to `format`.

The `:quoting <dialect>` option has superseded by the new dialect machinery and a new `:quoted` option that turns quoting on or off. You either use `:dialect <dialect>` instead or set a default dialect (via `set-dialect!`) and then use `{:quoted true}` in `format` calls where you want quoting.

Identifiers are automatically quoted if you specify a `:dialect` option to `format`, unless you also specify `:quoted false`.

The following options are no longer supported:
* `:allow-dashed-names?` -- if you provide dashed-names in v2, they will be left as-is if quoting is enabled, else they will be converted to snake_case (so you will either get `"dashed-names"` with quoting or `dashed_names` without).
* `:allow-namespaced-names?` -- this supported `foo/bar` column names in SQL which I'd like to discourage.
* `:namespace-as-table?` -- this is the default in v2: `:foo/bar` will be treated as `foo.bar` which is more in keeping with `next.jdbc`.
* `:parameterizer` -- this would add a lot of complexity to the formatting engine and I do not know how widely it was used (especially in its arbitrarily extensible form).
* `:return-param-names` -- this was added to v1 back in 2013 without an associated issue or PR so I've no idea what use case this was intended to support.

> Note: I expect some push back on those first three options and the associated behavior changes.

## DSL Changes

The general intent is that the data structure behind the DSL is unchanged, for the most part. The main deliberate change is the removal of the reader literals (and their associated helper functions) in favor of standardized syntax, e.g., `[:array [1 2 3]]` instead of either `#sql/array [1 2 3]` or `(sql/array [1 2 3])`.

The following new syntax has been added:

* `:array` -- used as a function to replace the `sql/array` / `#sql/array` machinery,
* `:between` -- this is now explicit syntax rather than being a special case in expressions,
* `:case` -- this is now explicit syntax,
* `:cast` -- `[:cast expr :type]` => `CAST( expr AS type )`,
* `:composite` -- explicit syntax to produce a comma-separated list of expressions, wrapped in parentheses,
* `:default` -- for `DEFAULT` values (in inserts) and for declaring column defaults in table definitions,
* `:inline` -- used as a function to replace the `sql/inline` / `#sql/inline` machinery,
* `:interval` -- used as a function to support `INTERVAL <n> <units>`, e.g., `[:interval 30 :days]`.
* `:lift` -- used as a function to prevent interpretation of a Clojure data structure as DSL syntax (e.g., when passing a vector or hash map as a parameter value) -- this should mostly be a replacement for `honeysql.format/value`,
* `:nest` -- used as a function to add an extra level of nesting (parentheses) around an expression,
* `:not` -- this is now explicit syntax,
* `:param` -- used as a function to replace the `sql/param` / `#sql/param` machinery,
* `:raw` -- used as a function to replace the `sql/raw` / `#sql/raw` machinery. Vector subexpressions inside a `[:raw ..]` expression are formatted to SQL and parameters. Other subexpressions are just turned into strings and concatenated. This is different to the v1 behavior but should be more flexible, since you can now embed `:inline`, `:param`, and `:lift` inside a `:raw` expression.

> Note 1: in 1.x, inlining a string `"foo"` produced `foo` but in 2.x it produces `'foo'`, i.e., string literals become SQL strings without needing internal quotes (1.x required `"'foo'"`).

Several additional pieces of syntax have also been added to support column
definitions in `CREATE TABLE` clauses, now that v2 supports DDL statement
construction: `:constraint`, `:foreign-key`, `:index`, `:primary-key`,
`:references`, `:unique`, and -- as noted above -- `:default`.

### select and function calls

You can now `SELECT` a function call more easily, using `[[...]]`. This was previously an error -- missing an alias -- but it was a commonly requested change, to avoid using `(sql/call ...)`:

```clojure
user=> (sql/format {:select [:a [:b :c] [[:d :e]] [[:f :g] :h]]})
;; select a (column), b (aliased to c), d (fn call), f (fn call, aliased to h):
["SELECT a, b AS c, D(e), F(g) AS h"]
```

On a related note, `sql/call` has been removed because it should never be needed now: `[:foo ...]` should always be treated as a function call, consistently, avoiding the special cases in v1 that necessitated the explicit `sql/call` syntax.

### select modifiers

HoneySQL 1.x provided a `:modifiers` clause (and a `modifiers`) helper as a way to "modify"
a `SELECT` to be `DISTINCT`. nilenso/honeysql-helpers extended that to support `:distinct-on`
a group of columns. In HoneySQL 2.x, you use `:select-distinct` and `:select-distinct-on`
(and their associated helpers) for that instead.

### set vs sset, set0, set1

The `:set` clause is dialect-dependent. In `:mysql`, it is ranked just before the `:where` clause. In all other dialects, it is ranked just before the `:from` clause. Accordingly, the `:set0` and `:set1` clauses are no longer supported (because they were workarounds in 1.x for this conflict). The helper is now called
`set` rather than `sset`, `set0`, and `set1` (so be aware of the conflict with `clojure.core/set`).

### exists

HoneySQL 1.x implemented `:exists` as part of the DSL, which was incorrect:
it should have been a function, and in 2.x it is:

```clojure
;; 1.x: EXISTS should never have been implemented as SQL syntax: it's an operator!
;; (sq/format {:exists {:select [:a] :from [:foo]}})
;;=> ["EXISTS (SELECT a FROM foo)"]

;; 2.x: select function call with an alias:
user=> (sql/format {:select [[[:exists {:select [:a] :from [:foo]}] :x]]})
["SELECT EXISTS (SELECT a FROM foo) AS x"]
```

## Extensibility

The protocols and multimethods in 1.x have all gone away. The primary extension point is `honey.sql/register-clause!` which lets you specify the new clause (keyword), the formatter function for it, and the existing clause that it should be ranked before (`format` processes the DSL in clause order).

You can also register new "functions" that can implement special syntax (such as `:array`, `:inline`, `:raw` etc above) via `honey.sql/register-fn!`. This accepts a "function" name as a keyword and a formatter which will generally be a function of two arguments: the function name (so formatters can be reused across different names) and a vector of the arguments the function should accept.

And, finally, you can register new operators that will be recognized in expressions via `honey.sql/register-op!`. This accepts an operator name as a keyword and optional named parameters to indicate whether the operator is `:variadic` (the default is strictly binary) and whether it should ignore operands that evaluate to `nil` (via `:ignore-nil`). The latter can make it easier to construct complex expressions programmatically without having to worry about conditionally removing "optional" (`nil`) values.

> Note: because of the changes in the extension machinery between v1 and v2, it is not possible to use the https://github.com/nilenso/honeysql-postgres library with HoneySQL v2 but the goal is to incorporate all of the syntax from that library into the core of HoneySQL.

## Helpers

The `honey.sql.helpers` namespace includes a helper function that corresponds to every supported piece of the data DSL understood by HoneySQL (v1 only had a limited set of helper functions). Unlike v1 helpers which sometimes had both a regular helper and a `merge-` helper, v2 helpers will all merge clauses by default (if that makes sense for the underlying DSL): use `:dissoc` if you want to force an overwrite.

The only helpers that have non-merging behavior are:
* `intersect`, `union`, `union-all`, `except`, and `except-all` which always wrap around their arguments,
* `delete`, `set`, `limit`, `offset`, `for`, and `values` which overwrite, rather than merge,
* `composite` which is a convenience for the `:composite` syntax mentioned above: `(composite :a :b)` is the same as `[:composite :a :b]` which produces `(a, b)`.
