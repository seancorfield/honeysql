;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.bigquery-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut]))

(deftest except-replace-tests
  (is (= ["SELECT * FROM table WHERE id = ?" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * EXCEPT (a, b, c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :except [:a :b :c]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * REPLACE (a * 100 AS b, 2 AS c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :replace [[[:* :a [:inline 100]] :b] [[:inline 2] :c]]]] :from [:table] :where [:= :id 1]})))
  (is (= ["SELECT * EXCEPT (a, b) REPLACE (2 AS c) FROM table WHERE id = ?" 1]
         (sut/format {:select [[:* :except [:a :b] :replace [[[:inline 2] :c]]]] :from [:table] :where [:= :id 1]}))))
