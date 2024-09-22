;; copyright (c) 2023-2024 sean corfield, all rights reserved

(ns honey.union-test
  (:refer-clojure :exclude [format])
  (:require [clojure.test :refer [deftest is]]
            [honey.sql :as sut]))

(deftest issue-451
  (is (=  [(str "SELECT ids.id AS id"
                " FROM ((SELECT dimension.human_readable_field_id AS id"
                " FROM dimension AS dimension"
                " WHERE (dimension.field_id = ?) AND (dimension.human_readable_field_id IS NOT NULL)"
                " LIMIT ?)"
                " UNION"
                " (SELECT dest.id AS id"
                " FROM field AS source"
                " LEFT JOIN table AS table ON source.table_id = table.id"
                " LEFT JOIN field AS dest ON dest.table_id = table.id"
                " WHERE (source.id = ?) AND (source.semantic_type IN (?)) AND (dest.semantic_type IN (?))"
                " LIMIT ?)) AS ids"
                " LIMIT ?")
           1
           1
           1
           "type/PK"
           "type/Name"
           1
           1]
          (-> {:select [[:ids.id :id]]
               :from   [[{:union
                          [{:nest
                            {:select [[:dimension.human_readable_field_id :id]]
                             :from   [[:dimension :dimension]]
                             :where  [:and
                                      [:= :dimension.field_id 1]
                                      [:not= :dimension.human_readable_field_id nil]]
                             :limit  1}}
                           {:nest
                            {:select    [[:dest.id :id]]
                             :from      [[:field :source]]
                             :left-join [[:table :table] [:= :source.table_id :table.id] [:field :dest] [:= :dest.table_id :table.id]]
                             :where     [:and
                                         [:= :source.id 1]
                                         [:in :source.semantic_type #{"type/PK"}]
                                         [:in :dest.semantic_type #{"type/Name"}]]
                             :limit     1}}]}
                         :ids]]
               :limit  1}
              (sut/format))))

)
