(ns honeysql.core-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest testing is]]
            [honeysql.core :refer :all]
            [honeysql.helpers :refer :all]))

;; TODO: more tests

(deftest test-select
  (let [m1 (-> (select :f.* :b.baz :c.quux [:b.bla "bla-bla"]
                       (call :now) (raw "@x := 10"))
               (un-select :c.quux)
               (modifiers :distinct)
               (from [:foo :f] [:baz :b])
               (join [[:clod :c] [:= :f.a :c.d] :left]
                     [:draq [:= :f.b :draq.x]])
               (where [:or
                       [:and [:= :f.a "bort"] [:not= :b.baz "gabba"]]
                       [:< 1 2 3]
                       [:in :f.e [1 2 3]]
                       [:between :f.e 10 20]])
               (merge-where [:not= nil :b.bla])
               (group :f.a)
               (having [:< 0 :f.e])
               (order-by [:b.baz :desc] :c.quux)
               (limit 50)
               (offset 10))]
    (testing "SQL data formats correctly"
      (is (= (format m1)
             ["SELECT DISTINCT f.*, b.baz, b.bla AS \"bla-bla\", NOW(), @x := 10 FROM foo AS f, baz AS b LEFT JOIN clod AS c ON f.a = c.d JOIN draq ON f.b = draq.x WHERE (((f.a = ? AND b.baz <> ?) OR (1 < 2 AND 2 < 3) OR (f.e IN (1, 2, 3)) OR f.e BETWEEN 10 AND 20) AND b.bla IS NOT NULL) GROUP BY f.a HAVING 0 < f.e ORDER BY b.baz DESC, c.quux LIMIT 50 OFFSET 10" "bort" "gabba"])))
    (testing "SQL data prints and reads correctly"
      (is (= m1 (read-string (pr-str m1)))))))
