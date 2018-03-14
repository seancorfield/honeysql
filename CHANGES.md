## 0.9.2

* Remove `nil` `:and` arguments for where. Fix #203. (@michaelblume)
* Fix nested `select` formatting. Fix #198. (@visibletrap)
* Limit value context to sequences in value positions. (@xiongtx)
* Avoid wrapping QUERY with parens while formatting `INSERT INTO ... QUERY`. (@emidln)
* Allow for custom name-transform-fn. Fix #193. (@madvas)
* Add :intersect to default-clause-priorities. (@kenfehling)
* Add `:parameterizer` `:none` for skipping `clojure.java.jdbc` parameter generation. (@arichiardi)
* Add ClojureScript self-host support. (@arichiardi)

## 0.9.1

* Add helper to inline values/prevent parameterization (@michaelblume)

## 0.9.0

BREAKING CHANGES:

* Some tuples used as values no longer work. See #168.

* Reprioritize WITH wrt UNION and UNION ALL (@emidln)
* Helpers for :with and :with-recursive clauses (@enaeher)
* Allow namespaced keywords and symbols for queries. (@jrdoane)
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
