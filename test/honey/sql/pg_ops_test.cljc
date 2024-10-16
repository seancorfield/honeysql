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
           (sql/format {:select [[[:|| :a :b :c] :x]]})))))
