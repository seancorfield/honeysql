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

(deftest struct-array-tests
  (is (= ["CREATE TABLE IF NOT EXISTS my_table (name STRING NOT NULL, my_struct STRUCT<name STRING NOT NULL, description STRING>, my_array ARRAY<STRING>)"]
         (sut/format (-> {:create-table [:my-table :if-not-exists]
                          :with-columns
                          [[:name :string [:not nil]]
                           [:my_struct [:bigquery/struct [:name :string [:not nil]] [:description :string]]]
                           [:my_array [:bigquery/array :string]]]}))))
  (is (= ["ALTER TABLE my_table ADD COLUMN IF NOT EXISTS name STRING, ADD COLUMN IF NOT EXISTS my_struct STRUCT<name STRING, description STRING>, ADD COLUMN IF NOT EXISTS my_array ARRAY<STRING>"]
         (sut/format {:alter-table [:my-table
                                    {:add-column [:name :string :if-not-exists]}
                                    {:add-column [:my_struct [:bigquery/struct [:name :string] [:description :string]] :if-not-exists]}
                                    {:add-column [:my_array [:bigquery/array :string] :if-not-exists]}]}))))

(deftest test-case-expr
  (is (= ["SELECT CASE foo WHEN ? THEN ? WHEN ? THEN foo / ? ELSE ? END FROM bar"
          1 -1 2 2 0]
         (sut/format
          {:select [[[:case-expr :foo
                      1 -1
                      2 [:/ :foo 2]
                      :else 0]]]
           :from [:bar]}))))
