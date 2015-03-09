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
