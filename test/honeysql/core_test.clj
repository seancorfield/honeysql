(ns honeysql.core-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest testing is]]
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all]))

;; TODO: more tests

(deftest test-insert
  (let [m1 (-> (insert-into :t)
               (columns     :column_one :column_two)
               (values [["one"] ["two"]])
               (returning   :column_one :column_two))
        m2 {:insert-into :t
            :columns     [:column_one :column_two]
            :values      [["one"] ["two"]]
            :returning   [:column_one :column_two]}
        m3 (sql/build m2)]
    (testing "Various construction methods are consistent"
      (is (= m1 m3)))
    (testing "SQL data formats correctly"
      (is (= (sql/format m1)
             ["INSERT INTO t (column_one, column_two) VALUES (?), (?) RETURNING column_one, column_two" "one" "two"])))))

(deftest test-select
  (let [m1 (-> (select :f.* :b.baz :c.quux [:b.bla :bla-bla]
                       :%now (sql/raw "@x := 10"))
               ;;(un-select :c.quux)
               (modifiers :distinct)
               (from [:foo :f] [:baz :b])
               (join :draq [:= :f.b :draq.x])
               (left-join [:clod :c] [:= :f.a :c.d])
               (right-join :bock [:= :bock.z :c.e])
               (where [:or
                       [:and [:= :f.a "bort"] [:not= :b.baz :?param1]]
                       [:< 1 2 3]
                       [:in :f.e [1 (sql/param :param2) 3]]
                       [:between :f.e 10 20]])
               ;;(merge-where [:not= nil :b.bla])
               (group :f.a)
               (having [:< 0 :f.e])
               (order-by [:b.baz :desc] :c.quux)
               (limit 50)
               (offset 10))
        m2 {:select [:f.* :b.baz :c.quux [:b.bla :bla-bla]
                     :%now (sql/raw "@x := 10")]
            ;;:un-select :c.quux
            :modifiers :distinct
            :from [[:foo :f] [:baz :b]]
            :join [:draq [:= :f.b :draq.x]]
            :left-join [[:clod :c] [:= :f.a :c.d]]
            :right-join [:bock [:= :bock.z :c.e]]
            :where [:or
                    [:and [:= :f.a "bort"] [:not= :b.baz :?param1]]
                    [:< 1 2 3]
                    [:in :f.e [1 (sql/param :param2) 3]]
                    [:between :f.e 10 20]]
            ;;:merge-where [:not= nil :b.bla]
            :group-by :f.a
            :having [:< 0 :f.e]
            :order-by [[:b.baz :desc] :c.quux]
            :limit 50
            :offset 10}
        m3 (sql/build m2)
        m4 (apply sql/build (apply concat m2))]
    (testing "Various construction methods are consistent"
      (is (= m1 m3)))
    (testing "SQL data formats correctly"
      (is (= (sql/format m1 {:param1 "gabba" :param2 2})
             ["SELECT DISTINCT f.*, b.baz, c.quux, b.bla AS bla_bla, now(), @x := 10 FROM foo f, baz b INNER JOIN draq ON f.b = draq.x LEFT JOIN clod c ON f.a = c.d RIGHT JOIN bock ON bock.z = c.e WHERE ((f.a = ? AND b.baz <> ?) OR (1 < 2 AND 2 < 3) OR (f.e in (1, ?, 3)) OR f.e BETWEEN 10 AND 20) GROUP BY f.a HAVING 0 < f.e ORDER BY b.baz DESC, c.quux LIMIT 50 OFFSET 10 "
              "bort" "gabba" 2])))
    (testing "SQL data prints and reads correctly"
      (is (= m1 (read-string (pr-str m1)))))))
