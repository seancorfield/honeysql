;; copyright (c) 2020-2021 sean corfield, all rights reserved

(ns honey.sql.helpers-test
  (:refer-clojure :exclude [update set group-by for])
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [honey.sql :as sql]
            [honey.sql.helpers :refer :all]))

(deftest test-select
  (let [m1 (-> (with [:cte (-> (select :*)
                               (from :example)
                               (where [:= :example-column 0]))])
               (select-distinct :f.* :b.baz :c.quux [:b.bla "bla-bla"]
                                :%now [[:raw "@x := 10"]])
               (from [:foo :f] [:baz :b])
               (join :draq [:= :f.b :draq.x])
               (left-join [:clod :c] [:= :f.a :c.d])
               (right-join :bock [:= :bock.z :c.e])
               (full-join :beck [:= :beck.x :c.y])
               (where [:or
                       [:and [:= :f.a "bort"] [:not= :b.baz :?param1]]
                       [:and [:< 1 2] [:< 2 3]]
                       [:in :f.e [1 [:param :param2] 3]]
                       [:between :f.e 10 20]])
               (group-by :f.a)
               (having [:< 0 :f.e])
               (order-by [:b.baz :desc] :c.quux [:f.a :nulls-first])
               (limit 50)
               (offset 10))
        m2 {:with [[:cte {:select [:*]
                          :from [:example]
                          :where [:= :example-column 0]}]]
            :select-distinct [:f.* :b.baz :c.quux [:b.bla "bla-bla"]
                              :%now [[:raw "@x := 10"]]]
            :from [[:foo :f] [:baz :b]]
            :join [:draq [:= :f.b :draq.x]]
            :left-join [[:clod :c] [:= :f.a :c.d]]
            :right-join [:bock [:= :bock.z :c.e]]
            :full-join [:beck [:= :beck.x :c.y]]
            :where [:or
                    [:and [:= :f.a "bort"] [:not= :b.baz :?param1]]
                    [:and [:< 1 2] [:< 2 3]]
                    [:in :f.e [1 [:param :param2] 3]]
                    [:between :f.e 10 20]]
            :group-by [:f.a]
            :having [:< 0 :f.e]
            :order-by [[:b.baz :desc] :c.quux [:f.a :nulls-first]]
            :limit 50
            :offset 10}]
    (testing "Various construction methods are consistent"
      (is (= m1 m2)))
    (testing "SQL data formats correctly"
      (is (= ["WITH cte AS (SELECT * FROM example WHERE example_column = ?) SELECT DISTINCT f.*, b.baz, c.quux, b.bla \"bla-bla\", now(), @x := 10 FROM foo AS f, baz AS b INNER JOIN draq ON f.b = draq.x LEFT JOIN clod AS c ON f.a = c.d RIGHT JOIN bock ON bock.z = c.e FULL JOIN beck ON beck.x = c.y WHERE ((f.a = ?) AND (b.baz <> ?)) OR ((? < ?) AND (? < ?)) OR (f.e IN (?, ?, ?)) OR f.e BETWEEN ? AND ? GROUP BY f.a HAVING ? < f.e ORDER BY b.baz DESC, c.quux ASC, f.a NULLS FIRST LIMIT ? OFFSET ?"
              0 "bort" "gabba" 1 2 2 3 1 2 3 10 20 0 50 10]
             (sql/format m1 {:params {:param1 "gabba" :param2 2}}))))
    #?(:clj (testing "SQL data prints and reads correctly"
              (is (= m1 (read-string (pr-str m1))))))
    #_(testing "SQL data formats correctly with alternate param naming"
        (is (= (sql/format m1 {:params {:param1 "gabba" :param2 2}})
               ["WITH cte AS (SELECT * FROM example WHERE example_column = $1) SELECT DISTINCT f.*, b.baz, c.quux, b.bla \"bla-bla\", now(), @x := 10 FROM foo AS f, baz AS b INNER JOIN draq ON f.b = draq.x LEFT JOIN clod AS c ON f.a = c.d RIGHT JOIN bock ON bock.z = c.e FULL JOIN beck ON beck.x = c.y WHERE ((f.a = $2) AND (b.baz <> $3)) OR (($4 < $5) AND ($6 < $7)) OR (f.e IN ($8, $9, $10)) OR f.e BETWEEN $11 AND $12 GROUP BY f.a HAVING $13 < f.e ORDER BY b.baz DESC, c.quux ASC, f.a NULLS FIRST LIMIT $14 OFFSET $15"
                0 "bort" "gabba" 1 2 2 3 1 2 3 10 20 0 50 10])))
    (testing "Locking"
      (is (= ["WITH cte AS (SELECT * FROM example WHERE example_column = ?) SELECT DISTINCT f.*, b.baz, c.quux, b.bla `bla-bla`, now(), @x := 10 FROM foo AS f, baz AS b INNER JOIN draq ON f.b = draq.x LEFT JOIN clod AS c ON f.a = c.d RIGHT JOIN bock ON bock.z = c.e FULL JOIN beck ON beck.x = c.y WHERE ((f.a = ?) AND (b.baz <> ?)) OR ((? < ?) AND (? < ?)) OR (f.e IN (?, ?, ?)) OR f.e BETWEEN ? AND ? GROUP BY f.a HAVING ? < f.e ORDER BY b.baz DESC, c.quux ASC, f.a NULLS FIRST LIMIT ? OFFSET ? LOCK IN SHARE MODE"
              0 "bort" "gabba" 1 2 2 3 1 2 3 10 20 0 50 10]
             (sql/format (assoc m1 :lock [:in-share-mode])
                         {:params {:param1 "gabba" :param2 2}
                          ;; to enable :lock
                          :dialect :mysql :quoted false}))))))

(deftest test-cast
  (is (= ["SELECT foo, CAST(bar AS integer)"]
         (sql/format {:select [:foo [[:cast :bar :integer]]]})))
  (is (= ["SELECT foo, CAST(bar AS integer)"]
         (sql/format {:select [:foo [[:cast :bar 'integer]]]}))))

(deftest test-value
  (is (= ["INSERT INTO foo (bar) VALUES (?)" {:baz "my-val"}]
         (->
           (insert-into :foo)
           (columns :bar)
           (values [[:lift {:baz "my-val"}]])
           sql/format)))
  (is (= ["INSERT INTO foo (a, b, c) VALUES (?, ?, ?), (?, ?, ?)"
          "a" "b" "c" "a" "b" "c"]
         (-> (insert-into :foo)
             (values [(array-map :a "a" :b "b" :c "c")
                      (hash-map :a "a" :b "b" :c "c")])
             sql/format))))

(deftest test-operators
  (testing "="
    (testing "with nil"
      (is (= ["SELECT * FROM customers WHERE name IS NULL"]
             (sql/format {:select [:*]
                          :from [:customers]
                          :where [:= :name nil]})))
      (is (= ["SELECT * FROM customers WHERE name = ?" nil]
             (sql/format {:select [:*]
                          :from [:customers]
                          :where [:= :name :?name]}
                         {:params {:name nil}})))))
  (testing "in"
    (doseq [[cname coll] [[:vector []] [:set #{}] [:list '()]]]
      (testing (str "with values from a " (name cname))
        (let [values (conj coll 1)]
          (is (= ["SELECT * FROM customers WHERE id IN (?)" 1]
                 (sql/format {:select [:*]
                              :from [:customers]
                              :where [:in :id values]})))
          (is (= ["SELECT * FROM customers WHERE id IN (?)" 1]
                 (sql/format {:select [:*]
                              :from [:customers]
                              :where [:in :id :?ids]}
                             {:params {:ids values}}))))))
    (testing "with more than one integer"
      (let [values [1 2]]
        (is (= ["SELECT * FROM customers WHERE id IN (?, ?)" 1 2]
               (sql/format {:select [:*]
                            :from [:customers]
                            :where [:in :id values]})))
        (is (= ["SELECT * FROM customers WHERE id IN (?, ?)" 1 2]
               (sql/format {:select [:*]
                            :from [:customers]
                            :where [:in :id :?ids]}
                           {:params {:ids values}})))))
    (testing "with more than one string"
      (let [values ["1" "2"]]
        (is (= ["SELECT * FROM customers WHERE id IN (?, ?)" "1" "2"]
               (sql/format {:select [:*]
                            :from [:customers]
                            :where [:in :id values]})
               (sql/format {:select [:*]
                            :from [:customers]
                            :where [:in :id :?ids]}
                           {:params {:ids values}})))))))

(deftest test-case
  (is (= ["SELECT CASE WHEN foo < ? THEN ? WHEN (foo > ?) AND ((foo MOD ?) = ?) THEN foo / ? ELSE ? END FROM bar"
          0 -1 0 2 0 2 0]
         (sql/format
          {:select [[[:case
                      [:< :foo 0] -1
                      [:and [:> :foo 0] [:= [:mod :foo 2] 0]] [:/ :foo 2]
                      :else 0]]]
           :from [:bar]})))
  (let [param1 1
        param2 2
        param3 "three"]
    (is (= ["SELECT CASE WHEN foo = ? THEN ? WHEN foo = bar THEN ? WHEN bar = ? THEN bar * ? ELSE ? END FROM baz"
            param1 0 param2 0 param3 "param4"]
           (sql/format
            {:select [[[:case
                        [:= :foo :?param1] 0
                        [:= :foo :bar] [:param :param2]
                        [:= :bar 0] [:* :bar :?param3]
                        :else "param4"]]]
             :from [:baz]}
            {:params
             {:param1 param1
              :param2 param2
              :param3 param3}})))))

(deftest test-raw
  (is (= ["SELECT 1 + 1 FROM foo"]
         (-> (select [[:raw "1 + 1"]])
             (from :foo)
             sql/format))))

(deftest test-call
  (is (= ["SELECT MIN(?) FROM ?" "time" "table"]
         (-> (select [[:min "time"]])
             (from "table")
             sql/format))))

(deftest join-test
  (testing "nil join"
    (is (= ["SELECT * FROM foo INNER JOIN x ON foo.id = x.id INNER JOIN y"]
           (-> (select :*)
               (from :foo)
               (join :x [:= :foo.id :x.id] :y nil)
               sql/format)))))

(deftest join-using-test
  (testing "nil join"
    (is (= ["SELECT * FROM foo INNER JOIN x USING (id) INNER JOIN y USING (foo, bar)"]
           (-> (select :*)
               (from :foo)
               (join :x [:using :id] :y [:using :foo :bar])
               sql/format)))))

(deftest inline-test
  (is (= ["SELECT * FROM foo WHERE id = 5"]
         (-> (select :*)
             (from :foo)
             (where [:= :id [:inline 5]])
             sql/format)))
  ;; testing for = NULL always fails in SQL -- this test is just to show
  ;; that an #inline nil should render as NULL (so make sure you only use
  ;; it in contexts where a literal NULL is acceptable!)
  (is (= ["SELECT * FROM foo WHERE id = NULL"]
         (-> (select :*)
             (from :foo)
             (where [:= :id [:inline nil]])
             sql/format))))

(deftest where-no-params-test
  (testing "where called with just the map as parameter - see #228"
    (let [sqlmap (-> (select :*)
                     (from :table)
                     (where [:= :foo :bar]))]
      (is (= ["SELECT * FROM table WHERE foo = bar"]
             (sql/format (apply merge sqlmap [])))))))

(deftest where-test
  (is (= ["SELECT * FROM table WHERE (foo = bar) AND (quuz = xyzzy)"]
         (-> (select :*)
             (from :table)
             (where [:= :foo :bar] [:= :quuz :xyzzy])
             sql/format)))
  (is (= ["SELECT * FROM table WHERE (foo = bar) AND (quuz = xyzzy)"]
         (-> (select :*)
             (from :table)
             (where [:= :foo :bar])
             (where [:= :quuz :xyzzy])
             sql/format))))

(deftest where-nil-params-test
  (testing "where called with nil parameters - see #246"
    (is (= ["SELECT * FROM table WHERE (foo = bar) AND (quuz = xyzzy)"]
           (-> (select :*)
               (from :table)
               (where nil [:= :foo :bar] nil [:= :quuz :xyzzy] nil)
               sql/format)))
    (is (= ["SELECT * FROM table"]
           (-> (select :*)
               (from :table)
               (where)
               sql/format)))
    (is (= ["SELECT * FROM table"]
           (-> (select :*)
               (from :table)
               (where nil nil nil nil)
               sql/format)))))

(deftest cross-join-test
  (is (= ["SELECT * FROM foo CROSS JOIN bar"]
         (-> (select :*)
             (from :foo)
             (cross-join :bar)
             sql/format)))
  (is (= ["SELECT * FROM foo AS f CROSS JOIN bar b"]
         (-> (select :*)
             (from [:foo :f])
             (cross-join [:bar :b])
             sql/format))))

(defn- stack-overflow-282 [num-ids]
  (let [ids (range num-ids)]
    (sql/format (reduce
                 where
                 {:select [[:id :id]]
                  :from   [:collection]
                  :where  [:= :personal_owner_id nil]}
                 (clojure.core/for [id ids]
                                   [:not-like :location [:raw (clojure.core/format "'/%d/%%'" id)]])))))

(deftest issue-282
  (is (= [(str "SELECT id AS id FROM collection"
               " WHERE (personal_owner_id IS NULL)"
               " AND (location NOT LIKE '/0/%')"
               " AND (location NOT LIKE '/1/%')")]
         (stack-overflow-282 2))))
