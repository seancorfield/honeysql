(ns honey.unhashable-test
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut]))

(deftest unhashable-value-509
  (let [unhashable (reify Object
                     (toString [_] "unhashable")
                     (hashCode [_] (throw (ex-info "Unsupported" {}))))]
    (is (= ["INSERT INTO table VALUES (?)" unhashable]
           (sut/format {:insert-into :table :values [[unhashable]]})))))
