;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.bigquery-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut])
  #?(:clj (:import (clojure.lang ExceptionInfo))))

(deftest except-replace-tests
  (is (= ["SELECT * FROM table WHERE id = ?" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * EXCEPT (a, b, c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :except [:a :b :c]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT table.* EXCEPT (a, b, c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:table.* :except [:a :b :c]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * REPLACE (a * 100 AS b, 2 AS c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :replace [[[:* :a [:inline 100]] :b] [[:inline 2] :c]]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * EXCEPT (a, b) REPLACE (2 AS c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :except [:a :b] :replace [[[:inline 2] :c]]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * REPLACE (a * ? AS b, ? AS c) FROM table WHERE id = ?" 100 2 1]
         (sut/format {:select [[:* :replace [[[:* :a 100] :b] [2 :c]]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * EXCEPT (a, b) REPLACE (? AS c) FROM table WHERE id = ?" 2 1]
         (sut/format {:select [[:* :except [:a :b] :replace [[2 :c]]]] :from [:table] :where [:= :id 1]}))))

(deftest bad-select-tests
  (is (thrown? ExceptionInfo
               (sut/format {:select [[:* :except [:a] :bad]]})))
  (is (thrown? ExceptionInfo
               (sut/format {:select [[:* :except]]})))
  (is (thrown? ExceptionInfo
               (sut/format {:select [[:foo :bar :quux]]}))))
