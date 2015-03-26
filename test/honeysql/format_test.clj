(ns honeysql.format-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest testing is are]]
            [honeysql.format :refer :all]))

(defmacro with-builder [& forms]
  `(binding [*builder* (StringBuilder.)]
     ~@forms
     (str *builder*)))

(deftest test-quote
  (are
    [qx res]
    (= (with-builder (apply quote-identifier "foo.bar.baz" qx)) res)
    [] "foo.bar.baz"
    [:style :mysql] "`foo`.`bar`.`baz`"
    [:style :mysql :split false] "`foo.bar.baz`")
  (are
    [x res]
    (= (with-builder (quote-identifier x)) res)
    3 "3"
    'foo "foo"
    :foo-bar "foo_bar")
  (is (= (with-builder (quote-identifier "*" :style :ansi)) "*")))

(defn make-clause [& args]
  (with-builder
    (apply format-clause args)))

(deftest test-cte
  (is (= (make-clause
          (first {:with [[:query {:select [:foo] :from [:bar]}]]}) nil)
         "WITH query AS SELECT foo FROM bar"))
  (is (= (make-clause
          (first {:with-recursive [[:query {:select [:foo] :from [:bar]}]]}) nil)
         "WITH RECURSIVE query AS SELECT foo FROM bar")))

(deftest insert-into
  (is (= (make-clause (first {:insert-into :foo}) nil)
         "INSERT INTO foo"))
  (is (= (make-clause (first {:insert-into [:foo {:select [:bar] :from [:baz]}]}) nil)
         "INSERT INTO foo SELECT bar FROM baz"))
  (is (= (make-clause (first {:insert-into [[:foo [:a :b :c]] {:select [:d :e :f] :from [:baz]}]}) nil)
         "INSERT INTO foo (a, b, c) SELECT d, e, f FROM baz")))
