;; copyright (c) sean corfield, all rights reserved

(ns honey.sql-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [honey.sql :as sut]))

(deftest mysql-tests
  (is (= ["SELECT * FROM `table` WHERE `id` = ?" 1]
         (#'sut/format {:select [:*] :from [:table] :where [:= :id 1]}
           {:dialect :mysql}))))

(deftest expr-tests
  (is (= ["id = ?" 1]
         (#'sut/format-expr [:= :id 1])))
  (is (= ["id + ?" 1]
         (#'sut/format-expr [:+ :id 1])))
  (is (= ["? + (? + quux)" 1 1]
         (#'sut/format-expr [:+ 1 [:+ 1 :quux]])))
  (is (= ["FOO(BAR(? + G(abc)), F(?, quux))" 2 1]
         (#'sut/format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])))
  (is (= ["id"]
         (#'sut/format-expr :id)))
  (is (= ["?" 1]
         (#'sut/format-expr 1)))
  (is (= ["INTERVAL ? DAYS" 30]
         (#'sut/format-expr [:interval 30 :days]))))

(deftest where-test
  (#'sut/format-where :where [:= :id 1]))

(deftest general-tests
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" = ?" 1]
         (#'sut/format {:select [:*] :from [:table] :where [:= :id 1]} {})))
  (is (= ["SELECT \"t\".* FROM \"table\" AS \"t\" WHERE \"id\" = ?" 1]
         (#'sut/format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]} {})))
  (is (= ["SELECT * FROM \"table\" GROUP BY \"foo\", \"bar\""]
         (#'sut/format {:select [:*] :from [:table] :group-by [:foo :bar]} {})))
  (is (= ["SELECT * FROM \"table\" GROUP BY DATE(\"bar\")"]
         (#'sut/format {:select [:*] :from [:table] :group-by [[:date :bar]]} {})))
  (is (= ["SELECT * FROM \"table\" ORDER BY \"foo\" DESC, \"bar\" ASC"]
         (#'sut/format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]} {})))
  (is (= ["SELECT * FROM \"table\" ORDER BY DATE(\"expiry\") DESC, \"bar\" ASC"]
         (#'sut/format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]} {})))
  (is (= ["SELECT * FROM \"table\" WHERE DATE_ADD(\"expiry\", INTERVAL ? DAYS) < NOW()" 30]
         (#'sut/format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]} {})))
  (is (= ["SELECT * FROM `table` WHERE `id` = ?" 1]
         (#'sut/format {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})))
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" IN (?,?,?,?)" 1 2 3 4]
         (#'sut/format {:select [:*] :from [:table] :where [:in :id [1 2 3 4]]} {}))))
