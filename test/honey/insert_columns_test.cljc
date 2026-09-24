;; copyright (c) 2021-2025 sean corfield, all rights reserved

(ns honey.insert-columns-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is testing]]
            [honey.sql :as sut]))

(deftest issue-618
  (testing "no override, explicit values order via array-map"
    (is (= ["INSERT INTO t (dname, did) VALUES (?, ?)" "Gizmo" 5]
           (sut/format {:insert-into :t
                        :values [(array-map :dname "Gizmo" :did 5)]})))
    (is (= ["INSERT INTO t (did, dname) VALUES (?, ?)" 5 "Gizmo"]
           (sut/format {:insert-into :t
                        :values [(array-map :did 5 :dname "Gizmo")]}))))
  (testing "override and explicit values order via array-map"
    (is (= ["INSERT INTO t (did, dname) VALUES (?, ?)" 5 "Gizmo"]
           (sut/format {:insert-into :t :columns [:did :dname]
                        :values [(array-map :dname "Gizmo" :did 5)]})))
    (is (= ["INSERT INTO t (did, dname) VALUES (?, ?)" 5 "Gizmo"]
           (sut/format {:insert-into [:t [:did :dname]]
                        :values [(array-map :dname "Gizmo" :did 5)]}))))
  (testing "override and implicit values order via hash-map"
    (is (= ["INSERT INTO t (did, dname) VALUES (?, ?)" 5 "Gizmo"]
           (sut/format {:insert-into :t :columns [:did :dname]
                        :values [(hash-map :dname "Gizmo" :did 5)]})))
    (is (= ["INSERT INTO t (did, dname) VALUES (?, ?)" 5 "Gizmo"]
           (sut/format {:insert-into [:t [:did :dname]]
                        :values [(hash-map :dname "Gizmo" :did 5)]})))))
