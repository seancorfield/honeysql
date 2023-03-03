# Changes

* 2.4.1002 -- 2023-03-03
  * Address [#474](https://github.com/seancorfield/honeysql/issues/474) by adding dot-selection special syntax.
  * Improve docstrings for PostgreSQL operators via PR [#473](https://github.com/seancorfield/honeysql/pull/473) [@holyjak](https://github.com/holyjak).
  * Address [#471](https://github.com/seancorfield/honeysql/issues/471) by supporting interspersed SQL keywords in function calls.
  * Fix [#467](https://github.com/seancorfield/honeysql/issues/467) by allowing single keywords (symbols) as a short hand for a single-element sequence in more constructs via PR [#470](https://github.com/seancorfield/honeysql/pull/470) [@p-himik](https://github.com/p-himik).
  * Address [#466](https://github.com/seancorfield/honeysql/issues/466) by treating `[:and]` as `TRUE` and `[:or]` as `FALSE`.
  * Fix [#465](https://github.com/seancorfield/honeysql/issues/465) to allow multiple columns in `:order-by` special syntax via PR [#468](https://github.com/seancorfield/honeysql/pull/468) [@p-himik](https://github.com/p-himik).
  * Fix [#464](https://github.com/seancorfield/honeysql/issues/464) by adding an optional type argument to `:array` via PR [#469](https://github.com/seancorfield/honeysql/pull/469) [@p-himik](https://github.com/p-himik).
  * Address [#463](https://github.com/seancorfield/honeysql/issues/463) by explaining `:quoted nil` via PR [#475](https://github.com/seancorfield/honeysql/pull/475) [@nharsch](https://github.com/nharsch).
  * Address [#462](https://github.com/seancorfield/honeysql/issues/462) by adding a note in the documentation for set operations, clarifying precedence issues.

* 2.4.980 -- 2023-02-15
  * Fix [#461](https://github.com/seancorfield/honeysql/issues/461) -- a regression introduced in 2.4.979 -- by restricting unary operators to just `+`, `-`, and `~` (bitwise negation).

* 2.4.979 -- 2023-02-11
  * Address [#459](https://github.com/seancorfield/honeysql/issues/459) by making all operators variadic (except `:=` and `:<>`).
  * Address [#458](https://github.com/seancorfield/honeysql/issues/458) by adding `registered-*?` predicates.

* 2.4.972 -- 2023-02-02
  * Address [#456](https://github.com/seancorfield/honeysql/issues/456) by allowing `format` to handle expressions (like 1.x could) as well as statements. This should aid with migration from 1.x to 2.x.

* 2.4.969 -- 2023-01-14
  * Fix [#454](https://github.com/seancorfield/honeysql/issues/454) by allowing `-` to be variadic.
  * Address [#452](https://github.com/seancorfield/honeysql/pull/452) by adding `:replace-into` to the core SQL supported, instead of just for the MySQL and SQLite dialects (so the latter is not needed yet).
  * Address [#451](https://github.com/seancorfield/honeysql/issues/451) by adding a test for it, showing how `:nest` produces the desired result.
  * Address [#447](https://github.com/seancorfield/honeysql/issues/447) by updating GitHub Actions and dependencies.
  * Address [#445](https://github.com/seancorfield/honeysql/issues/445) and [#453](https://github.com/seancorfield/honeysql/issues/453) by adding key/constraint examples to `CREATE TABLE` docs.

* 2.4.962 -- 2022-12-17
  * Fix `set-options!` (only `:checking` worked in 2.4.947).
  * Fix `:cast` formatting when quoting is enabled, via PR [#443](https://github.com/seancorfield/honeysql/pull/443) [duddlf23](https://github.com/duddlf23).
  * Fix [#441](https://github.com/seancorfield/honeysql/issues/441) by adding `:replace-into` to in-flight clause order (as well as registering it for the `:mysql` dialect).
  * Fix [#434](https://github.com/seancorfield/honeysql/issues/434) by special-casing `:'ARRAY`.
  * Fix [#433](https://github.com/seancorfield/honeysql/issues/433) by supporting additional `WITH` syntax, via PR [#432](https://github.com/seancorfield/honeysql/issues/432), [@MawiraIke](https://github.com/MawiraIke). _[Technically, this was in 2.4.947, but I kept the issue open while I wordsmithed the documentation]_
  * Address [#405](https://github.com/seancorfield/honeysql/issues/405) by adding `:numbered` option, which can also be set globally using `set-options!`.

* 2.4.947 -- 2022-11-05
  * Fix [#439](https://github.com/seancorfield/honeysql/issues/439) by rewriting how DDL options are processed; also fixes [#386](https://github.com/seancorfield/honeysql/issues/386) and [#437](https://github.com/seancorfield/honeysql/issues/437); **Whilst this is intended to be purely a bug fix, it has the potential to be a breaking change -- hence the version jump to 2.4!**
  * Fix [#438](https://github.com/seancorfield/honeysql/issues/438) by
  supporting options on `TRUNCATE`.
  * Address [#435](https://github.com/seancorfield/honeysql/issues/435) by showing `CREATE TEMP TABLE` etc.
  * Fix [#431](https://github.com/seancorfield/honeysql/issues/431) -- `WHERE false` differed between the DSL and the `where` helper.
  * Address [#430](https://github.com/seancorfield/honeysql/issues/430) by treating `:'` as introducing a name that should be treated literally and not formatted as a SQL entity (which respects quoting, dot-splitting, etc); this effectively expands the "escape hatch" introduced via [#352](https://github.com/seancorfield/honeysql/issues/352) in 2.2.868. _Note that the function context behavior formats as a SQL entity, rather than the usual SQL "keyword", whereas this new context is a literal transcription rather than as a SQL entity!_
  * Address [#427](https://github.com/seancorfield/honeysql/issues/427) by adding `set-options!`.
  * Address [#415](https://github.com/seancorfield/honeysql/issues/415) by supporting multiple column names in `ADD COLUMN`, `ALTER COLUMN`, `DROP COLUMN`, and `MODIFY COLUMN`.

* 2.3.928 -- 2022-09-04
  * Address [#425](https://github.com/seancorfield/honeysql/issues/425) by clarifying that `INTERVAL` as special syntax may be MySQL-specific and PostgreSQL uses difference syntax (because `INTERVAL` is a data type there).
  * Address [#423](https://github.com/seancorfield/honeysql/issues/423) by supporting `DEFAULT` values and `DEFAULT` rows in `VALUES`.
  * Address [#422](https://github.com/seancorfield/honeysql/issues/422) by auto-quoting unusual entity names when `:quoted` (and `:dialect`) are not specified, making HoneySQL more secure by default.
  * Fix [#421](https://github.com/seancorfield/honeysql/issues/421) by adding `:replace-into` for `:mysql` dialect.
  * Address [#419](https://github.com/seancorfield/honeysql/issues/419) by adding `honey.sql.protocols` and `InlineValue` with a `sqlize` function.
  * Address [#413](https://github.com/seancorfield/honeysql/issues/413) by flagging a lack of `WHERE` clause for `DELETE`, `DELETE FROM`, and `UPDATE` when `:checking :basic` (or `:checking :strict`).
  * Fix [#392](https://github.com/seancorfield/honeysql/issues/392) by adding support for `WITH` / (`NOT`) `MATERIALIZED` -- via PR [#420](https://github.com/seancorfield/honeysql/issues/420) [@robhanlon22](https://github.com/robhanlon22).

* 2.3.911 -- 2022-07-29
  * Address [#418](https://github.com/seancorfield/honeysql/issues/418) by documenting a potential "gotcha" with multi-column `IN` expressions (a change from HoneySQL 1.x).
  * Fix [#416](https://github.com/seancorfield/honeysql/issues/416) via PR [#417](https://github.com/seancorfield/honeysql/issues/417) from [@corasaurus-hex](https://github.com/corasaurus-hex) -- using the internal default state for the integrity assertion.
  * Address [#414](https://github.com/seancorfield/honeysql/issues/414) by providing an example of `ORDER BY` with a `CASE` expression.
  * Address [#412](https://github.com/seancorfield/honeysql/issues/412) by documenting options in a separate page and reorganizing the ToC structure.
  * Address [#409](https://github.com/seancorfield/honeysql/issues/409) by making docstring check for public helpers conditional.
  * Fix [#406](https://github.com/seancorfield/honeysql/issues/406) by adding `:alter-column` (which produces `MODIFY COLUMN` when the MySQL dialect is selected) and deprecating `:modify-column`.
  * Address [#401](https://github.com/seancorfield/honeysql/issues/401) by adding `register-dialect!` and `get-dialect`, and also making `add-clause-before`, `strop`, and `upper-case` public so that new dialects are easier to construct.

* 2.2.891 -- 2022-04-23
  * Address [#404](https://github.com/seancorfield/honeysql/issues/404) by documenting PostgreSQL's `ARRAY` constructor syntax and how to produce it.
  * Address parts of [#403](https://github.com/seancorfield/honeysql/issues/403) by improving the documentation for `:array` and also improving the exception that was thrown when it was misused.
  * Fix [#402](https://github.com/seancorfield/honeysql/issues/402) by allowing for expressions in `:insert-into` table.
  * Address [#400](https://github.com/seancorfield/honeysql/issues/400) by adding `:table` clause.
  * Address [#399](https://github.com/seancorfield/honeysql/issues/399) by correcting multi-column `RETURNING` clauses in docs and tests.
  * Fix [#398](https://github.com/seancorfield/honeysql/issues/398) by adding `honey.sql.pg-ops` namespace that registers PostgreSQL JSON and regex operators and provides symbolic names for "unwritable" operators (that contain `@`, `#`, or `~`).
  * Address [#396](https://github.com/seancorfield/honeysql/issues/396) by throwing an exception if you try to cache a SQL statement that includes an `IN ()` expression, using a named parameter for the `IN` values.
  * Fix [#394](https://github.com/seancorfield/honeysql/issues/394) by restoring HoneySQL 1.x's behavior when quoting.
  * Fix [#387](https://github.com/seancorfield/honeysql/issues/387) again.
  * Update CI to reflect Clojure 1.11 release (master -> 1.11; new master is 1.12).
  * Update `build-clj` to v0.8.0.

* 2.2.868 -- 2022-02-21
  * Address [#387](https://github.com/seancorfield/honeysql/issues/387) by making the function simpler.
  * Fix [#385](https://github.com/seancorfield/honeysql/issues/385) by quoting inlined UUIDs.
  * Address [#352](https://github.com/seancorfield/honeysql/issues/352) by treating `:'` as introducing a function name that should be formatted as a SQL entity (which respects quoting, dot-splitting, etc), rather than as a SQL "keyword".

* 2.2.861 -- 2022-01-30
  * Address [#382](https://github.com/seancorfield/honeysql/issues/382) by adding `:case-expr` for BigQuery support.
  * Address [#381](https://github.com/seancorfield/honeysql/issues/381) by adding `generic-helper-variadic` and `generic-helper-unary` to `honey.sql.helpers`.
  * Fix [#380](https://github.com/seancorfield/honeysql/issues/380) by correcting test for function type in `register-clause!` and `register-fn!`.

* 2.2.858 -- 2022-01-20
  * Address #377 by adding `honey.sql/map=` to convert a hash map into an equality condition (for a `WHERE` clause).
  * Address #351 by adding a `:cache` option to `honey.sql/format` (for Clojure only, not ClojureScript).
  * Address #281 by adding support for `SELECT * EXCEPT ..` and `SELECT * REPLACE ..` and `ARRAY<>` and `STRUCT<>` column types -- see [SQL Clause Reference - SELECT](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started/sql-clause-reference#select-select-distinct) and [SQL Clause Reference - DDL](https://cljdoc.org/d/com.github.seancorfield/honeysql/CURRENT/doc/getting-started/sql-clause-reference#ddl-clauses) respectively for more details.
  * Update `build-clj` to v0.6.7.

* 2.2.840 -- 2021-12-23
  * Fix #375 for `:nest` statement.
  * Fix #374 by removing aliasing of `:is` / `:is-not` -- this changes the behavior of `[:is-not :col true/false]` to be _correct_ and _include `NULL` values_. Using `:is` / `:is-not` with values that are not Boolean and not `nil` will produce invalid SQL.
  * Update test dependencies.
  * Update `build-clj` to v0.6.5.

* 2.1.833 -- 2021-12-03
  * Fix #372 by merging `:select-distinct-on` differently.
  * Add empty column list check for `SELECT` and several other clauses, when `:checking :basic` (or `:strict`) is provided.
  * Update `build-clj` to v0.6.0.

* 2.1.829 -- 2021-11-27
  * Fix #371 by treating the operand of `NOT` as a nested expression (so it is parenthesized unless it is a simple value).
  * Fix #370 by **always** parenthesizing the operand of `:nest`.
  * Address #369 by adding a big clarifying docstring to the `honey.sql.helpers` namespace pointing out that all helper functions are variadic, they are all `[& args]`, some have `:arglists` metadata to provide a more specific usage hint but those _all omit the optional first argument (the DSL hash map)_.
  * Fix #354 by supporting `DROP COLUMN IF EXISTS` / `ADD COLUMN IF NOT EXISTS`.
  * Update `build-clj` to v0.5.5.

* 2.1.818 -- 2021-10-04
  * Fix #367 by supporting parameters in subexpressions around `IS NULL` / `IS NOT NULL` tests.
  * Address #366 by introducing `:values-default-columns` option to control whether missing columns are treated as `NULL` or `DEFAULT` in `:values` clauses with sequences of hash maps.
  * Fix #365 -- a regression from 1.x -- where subclauses for `UNION`, `EXCEPT`, etc were incorrectly parenthesized.
  * Update `build-clj` to v0.5.0.

* 2.0.813 -- 2021-09-25
  * Address #364 by recommending how to handle PostgreSQL operators that contain `@`.
  * Fix #363 and #362 by aligning more closely the semantics of `:inline` syntax with the `:inline true` option. A side effect of this is that `[:inline [:param :foo]]` will now (correctly) inline the value of the parameter `:foo` whereas it previously produced `PARAMS SOURCE`. In addition, inlining has been extended to vector values, so `[:inline ["a" "b" "c"]]` will now produce `('a', 'b', 'c')` and `[:inline [:lift ["a" "b" "c"]]]` will now produce `['a', 'b', 'c']` which is what people seemed to expect (the behavior was previously unspecified).
  * Fix #353 by correcting handling of strings used as SQL entities (such as table names); this was a regression introduced by a recent enhancement to `:create-table`.
  * Fix #349 by adding an optional `:quoted` argument to `set-dialect!`.
  * Address #347 by adding example of adding a primary key to an existing table via `:add-index`.
  * Support `AS` aliasing in `DELETE FROM`.
  * Switch from `readme` to `test-doc-blocks` so all documentation is tested!
  * Clean up build/update deps.

* 2.0.783 -- 2021-08-15 (a.k.a "2.0 Gold")
  * Fixes #344 by no longer dropping the qualifier on columns in a `SET` clause _for the `:mysql` dialect only_; the behavior is unchanged for all other dialects.
  * Fixes #340 by making the "hyphen to space" logic more general so _operators_ containing `-` should retain the hyphen without special cases.
  * Documentation improvements: `:fetch`, `:lift`, `:limit`, `:offset`, `:param`, `:select`; also around JSON/PostgreSQL.
  * Link to the [HoneySQL web app](https://www.john-shaffer.com/honeysql/) in both the README and **Getting Started**.
  * Switch to `tools.build` for running tests and JAR building etc.

* 2.0.0-rc5 (for testing; 2021-07-17)
  * Fix #338 by producing `OFFSET n ROWS` (or `ROW` if `n` is 1) if `:fetch` is present or `:sqlserver` dialect is specified; and by producing `FETCH NEXT n ROWS ONLY` (or `ROW` is `n` is 1; or `FIRST` instead of `NEXT` if `:offset` is not present).
  * Fix #337 by switching to `clojure.test` even for ClojureScript.
  * Address #332 by improving `:cross-join` documentation.
  * Address #330 by improving exception when a non-entity is encountered where an entity is expected.
  * Fix `fetch` helper (it previously returned an `:offset` clause).
  * Fix bug in unrolling nested argument to `with-columns` helper.

* 2.0.0-rc3 (for testing; 2021-06-16)
  * Fix #328 by adding `:distinct` as special syntax, affecting an expression.
  * Address #327 by changing "unknown clause" error to including mention of "nil values" (which are also illegal).
  * Fix #327 by making single-argument helpers consistent with multi-argument helpers.
  * Support PostgreSQL's `&&` array operator.
  * Clarify how to `SELECT` a function expression (in **Getting Started**).
  * Update `test-runner`.

* 2.0.0-rc2 (for testing; 2021-05-10)
  * Fix #326 by allowing `ON`/`USING` to be optional and not dropping parameters on the floor.
  * Fix #325 by making the `%` function call syntax respect `:quoted true` and/or `:dialect` options, and also allowing for qualified column names. (PR from @lognush)
  * Add `:quoted-snake true` option to force conversion from kebab-case to snake_case when `:quoted true` or a `:dialect` is specified to `format`.
  * Update `test-runner`.

* 2.0.0-rc1 (for testing; 2021-05-06)
  * Fix #324 so that `insert-into` supports merging into another statement in all cases.
  * Fix #323 by supporting more than one SQL entity in `:on-conflict`.
  * Fix #321 by adding `:checking` mode. Currently only detects potential problems with `IN` clauses.

* 2.0.0-beta2 (for testing; 2021-04-13)
  * The documentation continues to be expanded and clarified in response to feedback!
  * Fix #322 by rewriting/simplifying `WHERE`/`HAVING` merge logic. **Important bug fix!**
  * Fix #310 by adding support for `FILTER`, `WITHIN GROUP`, and `ORDER BY` (as an expression), from [nilenso/honeysql-postgres](https://github.com/nilenso/honeysql-postgres) 0.4.112. These are [Special Syntax](doc/special-syntax.md) and there are also helpers for `filter` and `within-group` -- so **be careful about referring in all of `honey.sql.helpers`** since it will now shadow `clojure.core/filter` (it already shadows `for`, `group-by`, `into`, `partition-by`, `set`, and `update`).
  * Fix #308 by supporting join clauses in `join-by` (and correcting the helper docstring).

* 2.0.0-beta1 (for testing; 2021-04-09)
  * **The merging behavior of `where`/`having` is broken in Beta 1!**
  * Since Alpha 3, more documentation has been written and existing documentation clarified (addressing #300, #309, #313, #314).
  * Fix #319 by ensuring `register-clause!` is idempotent.
  * Fix #317 by dropping qualifiers in `:set` clauses (just like we do with `:insert` columns). Note that you can still use explicit _dotted_ names if you want table qualification.
  * Fix #316 by disallowing entity names containing `;` (to avoid SQL injection risks).
  * Fix #312 by adding `:raw` as a clause. There is no helper function equivalent (because it would be ambiguous whether you meant a function form -- `[:raw ..]` -- or a clause form -- `{:raw ..}`; and for the same reason, there is no `nest` helper function since that also works as a clause and as a function/special syntax).

* 2.0.0-alpha3 (for early testing; 2021-03-13)
  * Change coordinates to `com.github.seancorfield/honeysql` (although new versions will continue to be deployed to `seancorfield/honeysql` for a while -- see the [Clojars Verified Group Names policy](https://github.com/clojars/clojars-web/wiki/Verified-Group-Names)).
  * Support much richer range of syntax on `CREATE`/`DROP` statements in general, including columns, `TABLESPACE`, `CASCADE`, `WITH [NO] DATA`, etc.
  * Fix #306 by supporting `CREATE TABLE .. AS ..`.
  * Fix #305 by supporting more complex join clauses.
  * Fix #303 by supporting MySQL's `ON DUPLICATE KEY UPDATE`.
  * Fix #301 by adding support for `CREATE`/`DROP`/`REFRESH` on `MATERIALIZED VIEW`.
  * Add tests to confirm #299 does not affect 2.x.
  * Fix #297 by adding both `SELECT .. INTO ..` and `SELECT .. BULK COLLECT INTO ..`.
  * Fix #295 by adding docstrings to all helper functions (and adding an assert to ensure it stays that way as more are added in future).
  * Confirm the whole of the [nilenso/honeysql-postgres](https://github.com/nilenso/honeysql-postgres) is implemented out-of-the-box (#293, up to 0.3.104 -- see also #310 which brought parity up to 0.4.112).
  * Fix #292 by adding support for `SELECT TOP` and `OFFSET`/`FETCH`.
  * Fix #284 by adding support for `LATERAL` (as special syntax, with a helper).
  * Reconcile `where` behavior with recent 1.x changes (porting #283 to 2.x).
  * Fix #280 by adding `:escape` as special syntax for regular expression patterns.
  * Fix #277 by adding `:join-by`/`join-by` so that you can have multiple `JOIN`'s in a specific order.

* 2.0.0-alpha2 (for early testing)
  * Since Alpha 1, a lot more documentation has been written and docstrings have been added to most functions in `honey.sql.helpers`.
  * Numerous small improvements have been made to clauses and helpers around insert/upsert.

* 2.0.0-alpha1 (for early testing)
  * This is a complete rewrite/simplification of HoneySQL that provides just two namespaces:
    * `honey.sql` -- this is the primary API via the `format` function as well as the various extension points.
    * `honey.sql.helpers` -- provides a helper function for every piece of the DSL that is supported out-of-the-box.
  * The coordinates for HoneySQL 2.x are `com.github.seancorfield/honeysql` so it can be added to a project that already uses HoneySQL 1.x without any conflicts, making it easier to migrate piecemeal from 1.x to 2.x.

# HoneySQL pre-2.x Changes

* 1.0.461 -- 2021-02-22
  * **Fix #299 potential SQL injection vulnerability.**
  * Fix/Improve `merge-where` (and `merge-having`) behavior. #282 via #283 (@camsaul)

* 1.0.444 -- 2020-05-29
  * Fix #259 so column names are always unqualified in inserts. (@jrdoane)
  * Fix #257 by adding support for `cross-join` / `merge-cross-join` / `:cross-join`. (@dcj)
  * Switch dev/test pipeline to use CLI/`deps.edn` instead of Leiningen. Also add CI vi both CircleCI and GitHub Actions.
  * Switch to MAJOR.MINOR.COMMITS versioning.
  * Remove macrovich dependency as this is no longer needed with modern ClojureScript.
  * Add mention of `next.jdbc` everywhere `clojure.java.jdbc` was mentioned.

* 0.9.10 -- 2020-03-06
  * Fix #254 #255 by adding support for `except`. (@ted-coakley-otm)
  * Fix #253 properly by supporting `false` as well. (@ted-coakley-otm)
  * Add cljs testing to `deps.edn`, also multi-version clj testing and new `readme` testing.

* 0.9.9 -- 2020-03-02
  * Fix #253 by supporting non-sequential join expressions.

* 0.9.8 -- 2019-09-07
  * Fix #249 by adding `honeysql.format/*namespace-as-table?*` and `:namespace-as-table?` option to `format`. (@seancorfield)

* 0.9.7 -- 2019-09-07
  * Fix #248 by treating alias as "not a subquery" when generating SQL for it. (@seancorfield)
  * Fix #247 by reverting #132 / #131 so the default behavior is friendlier for namespace-qualified keywords used for table and column names, but adds `honeysql.format/*allow-namespaced-names?*` to restore the previous behavior. A `:allow-namespaced-names?` option has been adding to `format` to set this more easily. (@seancorfield)
  * Fix #235 by adding `set0` (`:set0`) and `set1` (`:set1`) variants of `sset` (`:set`) to support different placements of `SET` (before `FROM` or after `JOIN` respectively) that different databases require. See also #200. (@seancorfield)
  * Fix #162 by adding `composite`/`:composite` constructor for values. (@seancorfield)
  * Fix #139 by checking arguments to `columns`/`merge-columns` and throwing an exception if a single collection is supplied (instead of varargs). (@seancorfield)
  * Fix #128 by adding `truncate` support. (@seancorfield)
  * Fix #99 by adding a note to the first use of `select` in the README that column names can be keywords or symbols but not strings. (@seancorfield)

* 0.9.6 -- 2019-09-24
  * Filter `nil` conditions out of `where`/`merge-where`. Fix #246. (@seancorfield)
  * Fix reflection warning introduced in 0.9.5 (via PR #237).

* 0.9.5 -- 2019-09-07
  * Support JDK11 (update Midje). PR #238. (@camsaul)
  * Support Turkish user.language. PR #237. (@camsaul)
  * `format-predicate` now accepts `parameterizer` as a named argument (default `:jdbc`) to match `format`. PR #234. (@glittershark)

* 0.9.4 -- 2018-10-01
  * `#sql/inline nil` should produce `NULL`. Fix #221. (@seancorfield)
  * `#sql/inline :kw` should produce `"kw"`. Fix #224 via PR #225. (@vincent-dm) Note: this introduces a new protocol, `Inlinable`, which controls inline value rendering, and changes the behavior of `#sql/inline :foo/bar` to produce just `"bar"` (where it was probably invalid SQL before).
  * Alias expressions `[:col :alias]` are now checked to have exactly two elements. Fix #226. (@seancorfield)
  * Allow `where` and `merge-where` to be given no predicates. Fix #228 and PR #230. (@seancorfield, @arichiardi)
  * `as` alias is no longer split during quoting. Fix #221 and PR #231. (@gws)

## Earlier releases

Not all of these releases were tagged on GitHub and none of them have release notes on GitHub. Releases prior to 0.5.0 were not documented (although some were tagged on GitHub).

* 0.9.3
  * Support parameters in `#sql/raw`. Fix #219. (@seancorfield)
  * Add examples of table/column aliases to the README. Fix #215. (@seancorfield)
  * Refactor parameterizer to use multimethods. PR #214. (@xlevus)
  * Add examples of `raw` and `inline` to the README. Fix #213. (@seancorfield)
  * Add `register-parameterizer` for custom parameterizers. PR #209. (@juvenn)
  * Change `set` priority to after `join`. Fix #200. (@michaelblume)
  * Switch dependency version checker to deps.co. PR #197. (@danielcompton)
  * Support `join ... using( ... )`. Fix #188, PR #201. (@vincent-dm)
  * Add multi-version testing for Clojure 1.7, 1.8, 1.9, 1.10 (master) (@seancorfield)
  * Bring all dependencies up-to-date. (@seancorfield)
  * Add `run-tests.sh` to make it easier to run the same tests manually that run on Travis-CI. (@seancorfield)
  * Add `deps.edn` to support `clj`/`tools.deps.alpha`. (@seancorfield)
  * Expose `#sql/inline` data reader. (@seancorfield)

* 0.9.2
  * Remove `nil` `:and` arguments for where. Fix #203. (@michaelblume)
  * Fix nested `select` formatting. Fix #198. (@visibletrap)
  * Limit value context to sequences in value positions. (@xiongtx)
  * Avoid wrapping QUERY with parens while formatting `INSERT INTO ... QUERY`. (@emidln)
  * Allow for custom name-transform-fn. Fix #193. (@madvas)
  * Add :intersect to default-clause-priorities. (@kenfehling)
  * Add `:parameterizer` `:none` for skipping `next.jdbc` or `clojure.java.jdbc` parameter generation. (@arichiardi)
  * Add ClojureScript self-host support. (@arichiardi)

* 0.9.1
  * Add helper to inline values/prevent parameterization (@michaelblume)

* 0.9.0 --
  * BREAKING CHANGE: Some tuples used as values no longer work. See #168.
  * Reprioritize WITH wrt UNION and UNION ALL (@emidln)
  * Helpers for `:with` and `:with-recursive` clauses (@enaeher)
  * Ensure sequences act as function invocations when in value position (@joodie)
  * Correct generated arglist for helpers defined with defhelper (@michaelblume)
  * Don't depend on map iteration order, fix bug with multiple map types (@tomconnors)
  * Don't throw away namespace portion of keywords (@jrdoane)
  * Update CLJS dependencies (@michaelblume)
  * Add helpers for :with and :with-recursive clauses (@enaher)

* 0.8.2
  * Don't parenthesize the subclauses of a UNION, UNION ALL, or INTERSECT clause. (@rnewman)

* 0.8.1
  * Add priority for union/union-all (@seancorfield)

* 0.8.0
  * Get arglists right for generated helpers (@camsaul, @michaelblume)
  * Allow HoneySQL to be used from ClojureScript (@rnewman, @michaelblume)
  * BREAKING CHANGE: HoneySQL now requires Clojure 1.7.0 or above.

* 0.7.0
  * Parameterize numbers, properly handle NaN, Infinity, -Infinity (@akhudek)
  * Fix lock example in README (@michaelblume)
  * Allow joins without a predicate (@stuarth)
  * Escape quotes in quoted identifiers (@csummers)
  * Add support for INTERSECT (@jakemcc)
  * Upgrade Clojure dependency (@michaelblume)

* 0.6.3
  * Fix bug when SqlCall/SqlRaw object is first argument to another helper (@MichaelBlume)
  * Add support for :intersect clause (@jakemcc)

* 0.6.2
  * Support column names in :with clauses (@emidln)
  * Support preserving dashes in quoted names (@jrdoane)
  * Document correct use of the :union clause (@dball)
  * Tests for :union and :union-all (@dball)
  * Add fn-handler for CASE statement (@loganlinn)
  * Build/test with Clojure 1.7 (@michaelblume)
  * Refactors for clarity (@michaelblume)

* 0.6.1
  * Define parameterizable protocol on nil (@dball)

* 0.6.0
  * Convert seq param values to literal sql param lists (@dball)
  * Apply seq to sets when converting to sql (@dball)

* 0.5.3
  * Support exists syntax (@cddr)
  * Support locking selects (@dball)
  * Add sql array type and reader literal (@loganmhb)
  * Allow user to specify where in sort order NULLs fall (@mishok13)

* 0.5.2
  * Add value type to inhibit interpreting clojure sequences as subqueries (@MichaelParam)
  * Improve documentation (@hlship)
  * Add type hints to avoid reflection (@MichaelBlume)
  * Allow database-specific query parameterization (@icambron, @MichaelBlume)

* 0.5.1
  * Add :url to project.clj (@MichaelBlume)

* 0.5.0
  * Support basic common table expressions (:with, :with-recursive) (@akhudek)
  * Make clause order extensible (@MichaelBlume)
  * Support extended INSERT INTO...SELECT syntax (@ddellacosta)
  * Update clojure version to 1.6.0 (@MichaelBlume)
  * Implement ToSql on Object, vastly improving performance (@MichaelBlume)
  * Support CAST(foo AS type) (@senior)
  * Support postgres-native parameters (@icambron)
  * Support :full-join (@justindell)
  * Expose :arglist metadata in defhelper (@hlship)
  * Improvements to the documentation, especially showing some recently added features, such as inserts and updates.
