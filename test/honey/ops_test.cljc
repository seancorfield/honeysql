;; copyright (c) 2023-2025 sean corfield, all rights reserved

(ns honey.ops-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut]))

(deftest issue-454
  (is (= ["SELECT a - b - c AS x"]
         (-> {:select [[[:- :a :b :c] :x]]}
             (sut/format)))))

(deftest issue-566
  (is (= ["SELECT * FROM table WHERE a IS DISTINCT FROM b"]
         (-> {:select :* :from :table :where [:is-distinct-from :a :b]}
             (sut/format))))
  (is (= ["SELECT * FROM table WHERE a IS NOT DISTINCT FROM b"]
         (-> {:select :* :from :table :where [:is-not-distinct-from :a :b]}
             (sut/format)))))
