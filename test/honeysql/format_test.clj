(ns honeysql.format-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest testing is are]]
            [honeysql.format :refer :all]))

(deftest test-quote
  (are
    [qx res]
    (= (apply quote-identifier "foo.bar.baz" qx) res)
    [] "foo.bar.baz"
    [:style :mysql] "`foo`.`bar`.`baz`"
    [:style :mysql :split false] "`foo.bar.baz`")
  (are
    [x res]
    (= (quote-identifier x) res)
    3 "3"
    'foo "foo"
    :foo-bar "foo_bar")
  (is (= (quote-identifier "*" :style :ansi) "*")))

(deftest test-cte
  (is (= (format-clause
          (first {:with [[:query {:select [:foo] :from [:bar]}]]}) nil)
         "WITH query AS SELECT foo FROM bar"))
  (is (= (format-clause
          (first {:with-recursive [[:query {:select [:foo] :from [:bar]}]]}) nil)
         "WITH RECURSIVE query AS SELECT foo FROM bar")))

(deftest insert-into
  (is (= (format-clause (first {:insert-into :foo}) nil)
         "INSERT INTO foo"))
  (is (= (format-clause (first {:insert-into [:foo {:select [:bar] :from [:baz]}]}) nil)
         "INSERT INTO foo SELECT bar FROM baz"))
  (is (= (format-clause (first {:insert-into [[:foo [:a :b :c]] {:select [:d :e :f] :from [:baz]}]}) nil)
         "INSERT INTO foo (a, b, c) SELECT d, e, f FROM baz")))

(deftest exists-test
  (is (= (format {:exists {:select [:a] :from [:foo]}})
         ["EXISTS (SELECT a FROM foo)"]))
  (is (= (format {:select [:id]
                  :from [:foo]
                  :where [:exists {:select [1]
                                   :from [:bar]
                                   :where :deleted}]})
         ["SELECT id FROM foo WHERE EXISTS (SELECT 1 FROM bar WHERE deleted)"])))

(deftest array-test
  (is (= (format {:insert-into :foo
                  :columns [:baz]
                  :values [[#sql/array [1 2 3 4]]]})
         ["INSERT INTO foo (baz) VALUES (ARRAY[1, 2, 3, 4])"]))
  (is (= (format {:insert-into :foo
                  :columns [:baz]
                  :values [[#sql/array ["one" "two" "three"]]]})
         ["INSERT INTO foo (baz) VALUES (ARRAY[?, ?, ?])" "one" "two" "three"])))
