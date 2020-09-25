# Differences Between 1.x and 2.x

The goal of HoneySQL 1.x and earlier was to provide a DSL for vendor-neutral SQL, with the assumption that other libraries would provide the vendor-specific extensions to HoneySQL. HoneySQL 1.x's extension mechanism required quite a bit of internal knowledge (clause priorities and multiple multimethod extension points). It also used a number of custom record types, protocols, and data readers to provide various "escape hatches" in the DSL for representing arrays, function calls (in some situations), inlined values, parameters, and raw SQL, which led to a number of inconsistencies over time, as well as making some things very hard to express while other similar things were easy to express. Addressing bugs caused by vendor-specific differences and by some quirks of how SQL was generated gradually became harder and harder.

The goal of HoneySQL 2.x is to provide an easily-extensible DSL for SQL, supporting vendor-specific differences and extensions, that is as consistent as possible. A secondary goal is to make maintenance much easier by streamlining the machinery and reducing the number of different ways to write and/or extend the DSL.

The DSL itself -- the data structures that both versions convert to SQL and parameters via the `format` function -- is almost exactly the same between the two versions so that migration is relatively painless. The primary API -- the `format` function -- is preserved in 2.x, although the variadic options from 1.x have changed to an options hash map in 2.x as this is generally considered more idiomatic. See the **Option Changes** section below for the differences in the options supported.

## Group, Artifact, and Namespaces

HoneySQL 2.x uses the group ID `seancorfield` with the original artifact ID of `honeysql`, in line with the recommendations in Inside Clojure's post about the changes in the Clojure CLI: [Deprecated unqualified lib names](https://insideclojure.org/2020/07/28/clj-exec/).

In addition, HoneySQL 2.x contains different namespaces so you can have both versions on your classpath without introducing any conflicts. The primary API is now in `honey.sql` and the helpers are in `honey.sql.helpers`. A Spec for the DSL data structure is available in `honey.specs` (work in progress).

### HoneySQL 1.x

```clojure
;; in deps.edn:
honeysql {:mvn/version "1.0.444"}
;; or, more correctly:
honeysql/honeysql {:mvn/version "1.0.444"}

;; in use:
(ns my.project
  (:require [honeysql.core :as sql]))

...
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]})
  ;;=> ["SELECT * FROM table WHERE id = ?" 1]
```

The namespaces were:
* `honeysql.core` -- the primary API (`format`, etc),
* `honeysql.format` -- the logic for the formatting engine,
* `honeysql.helpers` -- helper functions to build the DSL,
* `honeysql.types` -- records, protocols, and data readers,
* `honeysql.util` -- internal utilities (macros).

### HoneySQL 2.x

```clojure
;; in deps.edn:
seancorfield/honeysql {:mvn/version "2.x"}

;; in use:
(ns my.project
  (:require [honey.sql :as sql]))

...
  (sql/format {:select [:*] :from [:table] :where [:= :id 1]})
  ;;=> ["SELECT * FROM table WHERE id = ?" 1]
```

The new namespaces are:
* `honey.sql` -- the primary API (just `format` now),
* `honey.sql.helpers` -- helper functions to build the DSL,
* `honey.specs` -- a description of the DSL using `clojure.spec.alpha`.

## API Changes

The primary API is just `honey.sql/format`. The `array`, `call`, `inline`, `param`, and `raw` functions have all become standard syntax in the DSL as functions (and their tagged literal equivalents have also gone away because they are no longer needed).

Other `honeysql.core` functions that no longer exist include: `build`, `qualify`, and `quote-identifier`. Many other public functions were essentially undocumented (neither mentioned in the README nor in the tests) and also no longer exist.

You can now select a non-ANSI dialect of SQL using the new `honey.sql/set-dialect!` function (which sets a default dialect for all `format` operations) or by passing the new `:dialect` option to the `format` function. `:ansi` is the default dialect (which will mostly incorporate PostgreSQL usage over time). Other dialects supported are `:mysql` (which has a different quoting strategy and uses a different ranking for the `:set` clause), `:oracle` (which is essentially the `:ansi` dialect but will control other things over time), and `:sqlserver` (which is essentially the `:ansi` dialect but with a different quoting strategy) and . Other dialects and changes may be added over time.

> Note: `:limit` and `:offset` are currently in the default `:ansi` dialect even though they are MySQL-specific. This is temporary as the dialects are being fleshed out. I expect to add `:top` for `:sqlserver` and `:offset` / `:fetch` for `:ansi`, at which point `:limit` / `:offset` will become MySQL-only.

## Option Changes

As noted above, the variadic options for `format` have been replaced by a single hash map as the optional second argument to `format`.

The `:quoting <dialect>` option has superseded by the new dialect machinery and a new `:quoted` option that turns quoting on or off. You either use `:dialect <dialect>` instead or set a default dialect (via `set-dialect!`) and then use `{:quoted true}` in `format` calls where you want quoting.

Identifiers are automatically quoted if you specify a `:dialect` option to `format`, unless you also specify `:quoted false`.

The following options are no longer supported:
* `:namespace-as-table?` -- TODO
* `:parameterizer` -- this would add a lot of complexity to the formatting engine and I do not know how widely it was used (especially in its arbitrarily extensible form).

## DSL Changes

You can now `SELECT` a function call more easily, using `[[...]]`. This was previously an error -- missing an alias -- but it was a commonly requested change, to avoid using `(sql/call ...)`:

```clojure
  (sql/format {:select [:a [:b :c] [[:d :e]] [[:f :g] :h]]})
  ;; select a (column), b (aliased to c), d (fn call), f (fn call, aliased to h):
  ;;=> ["SELECT a, b AS c, D(e), F(g) AS h"]

```

The `:set` clause is dialect-dependent. In `:mysql`, it is ranked just before the `:where` clause. In all other dialects, it is ranked just before the `:from` clause. Accordingly, the `:set0` and `:set1` clauses are no longer supported (because they were workarounds in 1.x for this conflict).

The following new syntax has been added:

* `:array` -- used as a function to replace the `sql/array` / `#sql/array` machinery,
* `:inline` -- used as a function to replace the `sql/inline` / `#sql/inline` machinery,
* `:interval` -- used as a function to support `INTERVAL <n> <units>`, e.g., `[:interval 30 :days]`.

> Note 1: in 1.x, inlining a string `"foo"` produced `foo` but in 2.x it produces `'foo'`, i.e., string literals become SQL strings without needing internal quotes (1.x required `"'foo'"`).

> Note 2: expect `:raw` to be added in some form before release.

## Extensibility

The protocols and multimethods in 1.x have all gone away. The primary extension point is `honey.sql/register-clause!` which lets you specify the new clause (keyword), the formatter function for it, and the existing clause that it should be ranked before (`format` processes the DSL in clause order).
