;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.cache-test
  (:refer-clojure :exclude [format group-by])
  (:require [clojure.core.cache.wrapped :as cache]
            [clojure.test :refer [deftest is]]
            [honey.sql :as sut]
            [honey.sql.helpers
             :refer [select-distinct from join left-join right-join where
                     group-by having order-by limit offset]]))

(def big-complicated-map
  (-> (select-distinct :f.* :b.baz :c.quux [:b.bla "bla-bla"]
                       [[:now]] [[:raw "@x := 10"]])
      (from [:foo :f] [:baz :b])
      (join :draq [:= :f.b :draq.x]
            :eldr [:= :f.e :eldr.t])
      (left-join [:clod :c] [:= :f.a :c.d])
      (right-join :bock [:= :bock.z :c.e])
      (where [:or
              [:and [:= :f.a "bort"] [:not= :b.baz [:param :param1]]]
              [:and [:< 1 2] [:< 2 3]]
              [:in :f.e [1 [:param :param2] 3]]
              [:between :f.e 10 20]])
      (group-by :f.a :c.e)
      (having [:< 0 :f.e])
      (order-by [:b.baz :desc] :c.quux [:f.a :nulls-first])
      (limit 50)
      (offset 10)))

(deftest cache-tests
  (let [cache (cache/basic-cache-factory {})]
    (is (= ["SELECT * FROM table WHERE id = ?" 1]
           (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                       {:cache cache})
           (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                       {:cache cache})))
    (is (= (sut/format {:select [:*] :from [:table] :where [:= :id 1]})
           (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                       {:cache cache})))
    (is (= (sut/format big-complicated-map {:params {:param1 "gabba" :param2 2}})
           (sut/format big-complicated-map {:cache cache :params {:param1 "gabba" :param2 2}})
           (sut/format big-complicated-map {:cache cache :params {:param1 "gabba" :param2 2}})))
    (is (= (sut/format big-complicated-map {:params {:param1 "foo" :param2 42}})
           (sut/format big-complicated-map {:cache cache :params {:param1 "foo" :param2 42}})
           (sut/format big-complicated-map {:cache cache :params {:param1 "foo" :param2 42}})))
    (println "Uncached, simple, embedded")
    (time (dotimes [_ 100000]
            (sut/format {:select [:*] :from [:table] :where [:= :id 1]})))
    (println "Cached, simple, embedded")
    (time (dotimes [_ 100000]
            (sut/format {:select [:*] :from [:table] :where [:= :id 1]} {:cache cache})))
    (println "Uncached, complex, mixed")
    (time (dotimes [_ 10000]
            (sut/format big-complicated-map {:params {:param1 "gabba" :param2 2}})))
    (println "Cached, complex, mixed")
    (time (dotimes [_ 10000]
            (sut/format big-complicated-map {:cache cache :params {:param1 "gabba" :param2 2}}))))
  (let [cache (cache/basic-cache-factory {})]
    (is (= ["SELECT * FROM table WHERE id = ?" 1]
           (sut/format {:select [:*] :from [:table] :where [:= :id :?id]}
                       {:cache cache :params {:id 1}})
           (sut/format {:select [:*] :from [:table] :where [:= :id :?id]}
                       {:cache cache :params {:id 1}})))
    (is (= (sut/format {:select [:*] :from [:table] :where [:= :id :?id]}
                       {:params {:id 2}})
           (sut/format {:select [:*] :from [:table] :where [:= :id :?id]}
                       {:cache cache :params {:id 2}})))))
