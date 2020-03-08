## 1.0.next in progress

* Switch dev/test pipeline to use CLI/`deps.edn` instead of Leiningen.
* Remove macrovich dependency as this is no longer needed with modern ClojureScript.
* Add mention of `next.jdbc` everywhere `clojure.java.jdbc` was mentioned.

## 0.9.10

* Fix #254 #255 by adding support for `except`. (@ted-coakley-otm)
* Fix #253 properly by supporting `false` as well. (@ted-coakley-otm)
* Add cljs testing to `deps.edn`, also multi-version clj testing and new `readme` testing.

## 0.9.9

* Fix #253 by supporting non-sequential join expressions.

## 0.9.8

* Fix #249 by adding `honeysql.format/*namespace-as-table?*` and `:namespace-as-table?` option to `format`. (@seancorfield)

## 0.9.7

* Fix #248 by treating alias as "not a subquery" when generating SQL for it. (@seancorfield)
* Fix #247 by reverting #132 / #131 so the default behavior is friendlier for namespace-qualified keywords used for table and column names, but adds `honeysql.format/*allow-namespaced-names?*` to restore the previous behavior. A `:allow-namespaced-names?` option has been adding to `format` to set this more easily. (@seancorfield)
* Fix #235 by adding `set0` (`:set0`) and `set1` (`:set1`) variants of `sset` (`:set`) to support different placements of `SET` (before `FROM` or after `JOIN` respectively) that different databases require. See also #200. (@seancorfield)
* Fix #162 by adding `composite`/`:composite` constructor for values. (@seancorfield)
* Fix #139 by checking arguments to `columns`/`merge-columns` and throwing an exception if a single collection is supplied (instead of varargs). (@seancorfield)
* Fix #128 by adding `truncate` support. (@seancorfield)
* Fix #99 by adding a note to the first use of `select` in the README that column names can be keywords or symbols but not strings. (@seancorfield)

## 0.9.6

* Filter `nil` conditions out of `where`/`merge-where`. Fix #246. (@seancorfield)
* Fix reflection warning introduced in 0.9.5 (via PR #237).

## 0.9.5

* Support JDK11 (update Midje). PR #238. (@camsaul)
* Support Turkish user.language. PR #237. (@camsaul)
* `format-predicate` now accepts `parameterizer` as a named argument (default `:jdbc`) to match `format`. PR #234. (@glittershark)

## 0.9.4

* `#sql/inline nil` should produce `NULL`. Fix #221. (@seancorfield)
* `#sql/inline :kw` should produce `"kw"`. Fix #224 via PR #225. (@vincent-dm) Note: this introduces a new protocol, `Inlinable`, which controls inline value rendering, and changes the behavior of `#sql/inline :foo/bar` to produce just `"bar"` (where it was probably invalid SQL before).
* Alias expressions `[:col :alias]` are now checked to have exactly two elements. Fix #226. (@seancorfield)
* Allow `where` and `merge-where` to be given no predicates. Fix #228 and PR #230. (@seancorfield, @arichiardi)
* `as` alias is no longer split during quoting. Fix #221 and PR #231. (@gws)

## 0.9.3

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

## 0.9.2

* Remove `nil` `:and` arguments for where. Fix #203. (@michaelblume)
* Fix nested `select` formatting. Fix #198. (@visibletrap)
* Limit value context to sequences in value positions. (@xiongtx)
* Avoid wrapping QUERY with parens while formatting `INSERT INTO ... QUERY`. (@emidln)
* Allow for custom name-transform-fn. Fix #193. (@madvas)
* Add :intersect to default-clause-priorities. (@kenfehling)
* Add `:parameterizer` `:none` for skipping `next.jdbc` or `clojure.java.jdbc` parameter generation. (@arichiardi)
* Add ClojureScript self-host support. (@arichiardi)

## 0.9.1

* Add helper to inline values/prevent parameterization (@michaelblume)

## 0.9.0

BREAKING CHANGES:

* Some tuples used as values no longer work. See #168.

* Reprioritize WITH wrt UNION and UNION ALL (@emidln)
* Helpers for :with and :with-recursive clauses (@enaeher)
* Ensure sequences act as function invocations when in value position (@joodie)
* Correct generated arglist for helpers defined with defhelper (@michaelblume)
* Don't depend on map iteration order, fix bug with multiple map types (@tomconnors)
* Don't throw away namespace portion of keywords (@jrdoane)
* Update CLJS dependencies (@michaelblume)
* Add helpers for :with and :with-recursive clauses (@enaher)

## 0.8.2

* Don't parenthesize the subclauses of a UNION, UNION ALL, or INTERSECT clause. (@rnewman)

## 0.8.1

* Add priority for union/union-all (@seancorfield)

## 0.8.0

* Get arglists right for generated helpers (@camsaul, @michaelblume)
* Allow HoneySQL to be used from Clojurescript (@rnewman, @michaelblume)
* BREAKING CHANGE: HoneySQL now requires Clojure 1.7.0 or above.

## 0.7.0

* Parameterize numbers, properly handle NaN, Infinity, -Infinity (@akhudek)
* Fix lock example in README (@michaelblume)
* Allow joins without a predicate (@stuarth)
* Escape quotes in quoted identifiers (@csummers)
* Add support for INTERSECT (@jakemcc)
* Upgrade Clojure dependency (@michaelblume)

## 0.6.3

* Fix bug when SqlCall/SqlRaw object is first argument to another helper (@MichaelBlume)
* Add support for :intersect clause (@jakemcc)

## 0.6.2

* Support column names in :with clauses (@emidln)
* Support preserving dashes in quoted names (@jrdoane)
* Document correct use of the :union clause (@dball)
* Tests for :union and :union-all (@dball)
* Add fn-handler for CASE statement (@loganlinn)
* Build/test with Clojure 1.7 (@michaelblume)
* Refactors for clarity (@michaelblume)

## 0.6.1

* Define parameterizable protocol on nil (@dball)

## 0.6.0

* Convert seq param values to literal sql param lists (@dball)
* Apply seq to sets when converting to sql (@dball)

## 0.5.3

* Support exists syntax (@cddr)
* Support locking selects (@dball)
* Add sql array type and reader literal (@loganmhb)
* Allow user to specify where in sort order NULLs fall (@mishok13)

## 0.5.2

* Add value type to inhibit interpreting clojure sequences as subqueries (@MichaelParam)
* Improve documentation (@hlship)
* Add type hints to avoid reflection (@MichaelBlume)
* Allow database-specific query parameterization (@icambron, @MichaelBlume)

## 0.5.1

* Add :url to project.clj (@MichaelBlume)

## 0.5.0

* Support basic common table expressions (:with, :with-recursive) (@akhudek)
* Make clause order extensible (@MichaelBlume)
* Support extended INSERT INTO...SELECT syntax (@ddellacosta)
* Update clojure version to 1.6.0 (@MichaelBlume)
* Implement ToSql on Object, vastly improving performance (@MichaelBlume)
* Support CAST(foo AS type) (@senior)
* Support postgres-native parameters (@icambron)
* Support :full-join (@justindell)
* Expose :arglist metadata in defhelper (@hlship)
* Improvements to the documentation, especially showing some recently added features, such as inserts
  and updates.
