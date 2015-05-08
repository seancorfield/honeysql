## 0.6.0 In development

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
