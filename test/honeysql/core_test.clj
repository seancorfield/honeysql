(ns honeysql.core-test
  (:refer-clojure :exclude [format group-by])
  (:require [clojure.test :refer [deftest testing is]]
            [honeysql.core :refer :all]))

;; TODO: more tests

(deftest test-select
  (let [sqlmap (-> (select :f.* :b.baz :c.quux (call :now) (raw "@x := 10"))
                   (modifiers :distinct)
                   (from [:foo :f] [:baz :b])
                   (join [[:clod :c] [:= :f.a :c.d] :left]
                         [:draq [:= :f.b :draq.x]])
                   (where [:or
                           [:and [:= :f.a "bort"] [:not= :b.baz "gabba"]]
                           [:in :f.e [1 2 3]]
                           [:between :f.e 10 20]])
                   (group-by :f.a)
                   (having [:< 0 :f.e])
                   (order-by [:b.baz :desc] :c.quux)
                   (limit 50)
                   (offset 10))]
    (testing "sqlmap formats correctly"
      (is (= (format sqlmap)
             ["SELECT DISTINCT f.*, b.baz, c.quux, NOW(), @x := 10 FROM foo AS f, baz AS b LEFT JOIN clod AS c ON (f.a = c.d) JOIN draq ON (f.b = draq.x) WHERE (((f.a = ?) AND (b.baz != ?)) OR (f.e IN (1, 2, 3)) OR f.e BETWEEN 10 AND 20) GROUP BY f.a HAVING (0 < f.e) ORDER BY b.baz DESC, c.quux LIMIT 50 OFFSET 10"
              "bort" "gabba"])))
    (testing "sqlmap prints and reads correctly"
      (is (= sqlmap (read-string (pr-str sqlmap)))))))