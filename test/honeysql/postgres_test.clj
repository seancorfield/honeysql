(ns honeysql.postgres-test
  (:refer-clojure :exclude [update])
  (:require [honeysql.postgres.format :refer :all]
            [honeysql.postgres.helpers :refer :all]
            [honeysql.helpers :refer :all]
            [honeysql.format :as sql]
            [clojure.test :refer :all]))

(deftest upsert-test
  (testing "upsert sql generation for postgresql"
    (is (= ["INSERT INTO distributors d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc"]
           (-> (insert-into [:distributors :d])
               (values [{:did 5 :dname "Gizmo Transglobal"}
                        {:did 6 :dname "Associated Computing, Inc"}])
               (upsert (-> (on-conflict :did)
                           (do-update-set :dname)))
               (returning :d.*)
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (7, ?) ON CONFLICT (did) DO NOTHING" "Redline GmbH"]
           (-> (insert-into :distributors)
               (values [{:did 7 :dname "Redline GmbH"}])
               (upsert (-> (on-conflict :did)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (9, ?) ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING" "Antwerp Design"]
           (-> (insert-into :distributors)
               (values [{:did 9 :dname "Antwerp Design"}])
               (upsert (-> (on-conflict-constraint :distributors_pkey)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (10, ?), (11, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname" "Pinp Design" "Foo Bar Works"]
           (sql/format {:insert-into :distributors
                        :values [{:did 10 :dname "Pinp Design"}
                                 {:did 11 :dname "Foo Bar Works"}]
                        :upsert {:on-conflict :did
                                 :do-update-set [:dname]}})))))

(deftest returning-test
  (testing "returning clause in sql generation for postgresql"
    (is (= ["DELETE FROM distributors WHERE did > 10 RETURNING *"]
           (sql/format {:delete-from :distributors
                        :where [:> :did :10]
                        :returning [:*] })))
    (is (= ["UPDATE distributors SET dname = ? WHERE did = 2 RETURNING did dname" "Foo Bar Designs"]
           (-> (update :distributors)
               (sset {:dname "Foo Bar Designs"})
               (where [:= :did :2])
               (returning [:did :dname])
               sql/format)))))
