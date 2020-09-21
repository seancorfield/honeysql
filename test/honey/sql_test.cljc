;; copyright (c) sean corfield, all rights reserved

(ns honey.sql-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [honey.sql :as sut]))

(deftest mysql-tests
  (is (= ["SELECT * FROM `table` WHERE `id` = ?" 1]
         (#'sut/sql-format {:select [:*] :from [:table] :where [:= :id 1]}
           {:dialect :mysql}))))
