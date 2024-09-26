(ns honey.util-test
  (:refer-clojure :exclude [str])
  (:require [clojure.test :refer [deftest is are]]
            [honey.sql.util :as sut]))

(deftest str-test
  (are [arg1 result] (= result (sut/str arg1))
    nil   ""
    1     "1"
    "foo" "foo"
    :foo  ":foo")
  (are [arg1 arg2 result] (= result (sut/str arg1 arg2))
    nil  nil   ""
    nil  1     "1"
    1    nil   "1"
    1    2     "12"
    :foo "bar" ":foobar")
  (are [arg1 arg2 arg3 result] (= result (sut/str arg1 arg2 arg3))
    nil  nil   nil  ""
    nil  1     nil  "1"
    1    nil   nil  "1"
    1    nil   2    "12"
    :foo "bar" 'baz ":foobarbaz")
  (are [args result] (= result (apply sut/str args))
    (range 10) "0123456789"
    []         ""))

(deftest join-test
  (is (= "0123456789" (sut/join "" (range 10))))
  (is (= "1" (sut/join "" [1])))
  (is (= "" (sut/join "" [])))
  (is (= "0, 1, 2, 3, 4, 5, 6, 7, 8, 9" (sut/join ", " (range 10))))
  (is (= "1" (sut/join ", " [1])))
  (is (= "" (sut/join ", " [])))

  (is (= "0_0, 1_1, 2_2, 3_3, 4_4, 5_5, 6_6, 7_7, 8_8, 9_9"
         (sut/join ", " (map #(sut/str % "_" %)) (range 10))))
  (is (= "1_1"
         (sut/join ", " (map #(sut/str % "_" %)) [1])))
  (is (= ""
         (sut/join ", " (map #(sut/str % "_" %)) [])))

  (is (= "1, 2, 3, 4"
         (sut/join ", " (remove nil?) [1 nil 2 nil 3 nil nil nil 4])))
  (is (= "" (sut/join ", " (remove nil?) [nil nil nil nil]))))
