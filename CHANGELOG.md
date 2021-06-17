# Changes

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
