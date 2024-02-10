;; copyright (c) 2023 sean corfield, all rights reserved

(ns honey.ops-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut]))

(deftest issue-454
  (is (= ["SELECT a - b - c AS x"]
         (-> {:select [[[:- :a :b :c] :x]]}
             (sut/format)))))
