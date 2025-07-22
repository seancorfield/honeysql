;; copyright (c) 2022-2024 sean corfield, all rights reserved

(ns honey.sql.pg-ops-test
  (:require [clojure.test :refer [deftest is testing]]
            [honey.sql :as sql]
            [honey.sql.pg-ops :as sut]))

(deftest pg-op-tests
  (testing "built-in ops"
    (is (= ["SELECT a || b AS x"]
           (sql/format {:select [[[:|| :a :b] :x]]})))
    (is (= ["SELECT a - b AS x"]
           (sql/format {:select [[[:- :a :b] :x]]}))))
  (testing "writable ops"
    (is (= ["SELECT a -> b AS x"]
           (sql/format {:select [[[:-> :a :b] :x]]})))
    (is (= ["SELECT a ->> b AS x"]
           (sql/format {:select [[[:->> :a :b] :x]]})))
    (is (= ["SELECT a #> b AS x"]
           (sql/format {:select [[[:#> :a :b] :x]]})))
    (is (= ["SELECT a #>> b AS x"]
           (sql/format {:select [[[:#>> :a :b] :x]]})))
    (is (= ["SELECT a ?? b AS x"]
           (sql/format {:select [[[:? :a :b] :x]]})))
    (is (= ["SELECT a ??| b AS x"]
           (sql/format {:select [[[:?| :a :b] :x]]})))
    (is (= ["SELECT a ??& b AS x"]
           (sql/format {:select [[[:?& :a :b] :x]]})))
    (is (= ["SELECT a #- b AS x"]
           (sql/format {:select [[[:#- :a :b] :x]]}))))
  (testing "named ops"
    (is (= ["SELECT a @> b AS x"]
           (sql/format {:select [[[sut/at> :a :b] :x]]})))
    (is (= ["SELECT a <@ b AS x"]
           (sql/format {:select [[[sut/<at :a :b] :x]]})))
    (is (= ["SELECT a @?? b AS x"]
           (sql/format {:select [[[sut/at? :a :b] :x]]})))
    (is (= ["SELECT a @@ b AS x"]
           (sql/format {:select [[[sut/atat :a :b] :x]]}))))
  (testing "variadic ops"
    (is (= ["SELECT a -> b -> c AS x"]
           (sql/format {:select [[[:-> :a :b :c] :x]]})))
    (is (= ["SELECT a || b || c AS x"]
           (sql/format {:select [[[:|| :a :b :c] :x]]}))))
  (testing "named parameter operator"
    (is (= ["SELECT MAKE_INTERVAL(secs => ?)" 10]
           (sql/format {:select [[[:make_interval [:=> :secs 10]]]]})))
    (is (= ["SELECT MAKE_INTERVAL(secs => ?, mins => ?)" 10 5]
           (sql/format {:select [[[:make_interval [:=> :secs 10] [:=> :mins 5]]]]})))
    (is (= ["SELECT MAKE_INTERVAL(secs => ?)" 42]
           (sql/format {:select [[[:make_interval [sut/=> :secs 42]]]]})))
    (is (= ["SELECT DATE_TRUNC(field => ?, source => ?)" "month" "2023-06-15"]
           (sql/format {:select [[[:date_trunc [:=> :field "month"] [:=> :source "2023-06-15"]]]]})))))
