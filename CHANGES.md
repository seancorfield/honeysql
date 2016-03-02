## 0.6.4 In development

## 0.6.3

Fix bug when SqlCall/SqlRaw object is first argument to another helper (@MichaelBlume)

* Add support for :intersect clause (@jakemcc)

## 0.6.2

Support column names in :with clauses (@emidln)
Support preserving dashes in quoted names (@jrdoane)
Document correct use of the :union clause (@dball)
Tests for :union and :union-all (@dball)
Add fn-handler for CASE statement (@loganlinn)
Build/test with Clojure 1.7 (@michaelblume)
Refactors for clarity (@michaelblume)

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
