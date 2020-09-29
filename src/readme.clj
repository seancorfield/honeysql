(ns readme (:require [seancorfield.readme]))























(seancorfield.readme/defreadme readme-25
(refer-clojure :exclude '[for group-by set update])
(require '[honey.sql :as sql]
         ;; caution: this overwrites for, group-by, set, and update
         '[honey.sql.helpers :refer :all :as helpers])
)



(seancorfield.readme/defreadme readme-34
(def sqlmap {:select [:a :b :c]
             :from   [:foo]
             :where  [:= :f.a "baz"]})
)







(seancorfield.readme/defreadme readme-46
(sql/format sqlmap)
=> ["SELECT a, b, c FROM foo WHERE f.a = ?" "baz"]
)

















(seancorfield.readme/defreadme readme-67
(def q-sqlmap {:select [:foo/a :foo/b :foo/c]
               :from   [:foo]
               :where  [:= :foo/a "baz"]})
(sql/format q-sqlmap)
=> ["SELECT foo.a, foo.b, foo.c FROM foo WHERE foo.a = ?" "baz"]
)







(seancorfield.readme/defreadme readme-81
(-> (select :a :b :c)
    (from :foo)
    (where [:= :f.a "baz"]))
)



(seancorfield.readme/defreadme readme-89
(= (-> (select :*) (from :foo))
   (-> (from :foo) (select :*)))
=> true
)



(seancorfield.readme/defreadme readme-97
(-> sqlmap (select :d))
=> '{:from [:foo], :where [:= :f.a "baz"], :select [:a :b :c :d]}
)



(seancorfield.readme/defreadme readme-104
(-> sqlmap
    (dissoc :select)
    (select :*)
    (where [:> :b 10])
    sql/format)
=> ["SELECT * FROM foo WHERE (f.a = ?) AND (b > ?)" "baz" 10]
)



(seancorfield.readme/defreadme readme-115
(-> (select :*)
    (from :foo)
    (where [:= :a 1] [:< :b 100])
    sql/format)
=> ["SELECT * FROM foo WHERE (a = ?) AND (b < ?)" 1 100]
)




(seancorfield.readme/defreadme readme-126
(-> (select :a [:b :bar] :c [:d :x])
    (from [:foo :quux])
    (where [:= :quux.a 1] [:< :bar 100])
    sql/format)
=> ["SELECT a, b AS bar, c, d AS x FROM foo quux WHERE (quux.a = ?) AND (bar < ?)" 1 100]
)










(seancorfield.readme/defreadme readme-143
(-> (insert-into :properties)
    (columns :name :surname :age)
    (values
     [["Jon" "Smith" 34]
      ["Andrew" "Cooper" 12]
      ["Jane" "Daniels" 56]])
    (sql/format {:pretty? true}))
=> ["
INSERT INTO properties (name, surname, age)
VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)
"
"Jon" "Smith" 34 "Andrew" "Cooper" 12 "Jane" "Daniels" 56]
)





(seancorfield.readme/defreadme readme-162
(-> (insert-into :properties)
    (values [{:name "John" :surname "Smith" :age 34}
             {:name "Andrew" :surname "Cooper" :age 12}
             {:name "Jane" :surname "Daniels" :age 56}])
    (sql/format {:pretty? true}))
=> ["
INSERT INTO properties (name, surname, age)
VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)
"
"John" "Smith" 34
"Andrew" "Cooper"  12
"Jane" "Daniels" 56]
)





(seancorfield.readme/defreadme readme-181
(let [user-id 12345
      role-name "user"]
  (-> (insert-into :user_profile_to_role)
      (values [{:user_profile_id user-id
                :role_id         (-> (select :id)
                                     (from :role)
                                     (where [:= :name role-name]))}])
      (sql/format {:pretty? true})))

=> ["
INSERT INTO user_profile_to_role (user_profile_id, role_id)
VALUES (?, (SELECT id FROM role WHERE name = ?))
"
12345
"user"]
)

(seancorfield.readme/defreadme readme-199
(-> (select :*)
    (from :foo)
    (where [:in :foo.a (-> (select :a) (from :bar))])
    sql/format)
=> ["SELECT * FROM foo WHERE (foo.a in (SELECT a FROM bar))"]
)





(seancorfield.readme/defreadme readme-211
(-> (insert-into :comp_table)
    (columns :name :comp_column)
    (values
     [["small" (composite 1 "inch")]
      ["large" (composite 10 "feet")]])
    (sql/format {:pretty? true}))
=> ["
INSERT INTO comp_table (name, comp_column)
VALUES (?, (?, ?)), (?, (?, ?))
"
"small" 1 "inch" "large" 10 "feet"]
)





(seancorfield.readme/defreadme readme-229
(-> (helpers/update :films)
    (set {:kind "dramatic"
           :watched [:+ :watched 1]})
    (where [:= :kind "drama"])
    (sql/format {:pretty? true}))
=> ["
UPDATE films SET kind = ?, watched = (watched + ?)
WHERE kind = ?
"
"dramatic"
1
"drama"]
)












(seancorfield.readme/defreadme readme-255
(-> (delete-from :films)
    (where [:<> :kind "musical"])
    (sql/format))
=> ["DELETE FROM films WHERE kind <> ?" "musical"]
)



(seancorfield.readme/defreadme readme-264
(-> (delete [:films :directors])
    (from :films)
    (join :directors [:= :films.director_id :directors.id])
    (where [:<> :kind "musical"])
    (sql/format {:pretty? true}))
=> ["
DELETE films, directors
FROM films
INNER JOIN directors ON films.director_id = directors.id
WHERE kind <> ?
"
"musical"]
)



(seancorfield.readme/defreadme readme-281
(-> (truncate :films)
    (sql/format))
=> ["TRUNCATE films"]
)





(seancorfield.readme/defreadme readme-291
(sql/format {:union [(-> (select :*) (from :foo))
                     (-> (select :*) (from :bar))]})
=> ["SELECT * FROM foo UNION SELECT * FROM bar"]
)





(seancorfield.readme/defreadme readme-301
(-> (select :%count.*) (from :foo) sql/format)
=> ["SELECT count(*) FROM foo"]
)
(seancorfield.readme/defreadme readme-305
(-> (select :%max.id) (from :foo) sql/format)
=> ["SELECT max(id) FROM foo"]
)







(seancorfield.readme/defreadme readme-316
(-> (select :id)
    (from :foo)
    (where [:= :a :?baz])
    (sql/format :params {:baz "BAZ"}))
=> ["SELECT id FROM foo WHERE a = ?" "BAZ"]
)






(seancorfield.readme/defreadme readme-329
(def call-qualify-map
  (-> (select [[:foo :bar]] [[:raw "@var := foo.bar"]])
      (from :foo)
      (where [:= :a [:param :baz]] [:= :b [:inline 42]])))
)
(seancorfield.readme/defreadme readme-335
call-qualify-map
=> '{:where [:and [:= :a [:param :baz]] [:= :b [:inline 42]]]
     :from (:foo)
     :select [[[:foo :bar]] [[:raw "@var := foo.bar"]]]}
)
(seancorfield.readme/defreadme readme-341
(sql/format call-qualify-map {:params {:baz "BAZ"}})
=> ["SELECT foo(bar), @var := foo.bar FROM foo WHERE (a = ?) AND (b = 42)" "BAZ"]
)






(seancorfield.readme/defreadme readme-351
(-> (insert-into :sample)
    (values [{:location [:ST_SetSRID
                         [:ST_MakePoint 0.291 32.621]
                         [:cast 4325 :integer]]}])
    (sql/format {:pretty? true}))
=> ["
INSERT INTO sample (location)
VALUES (ST_SetSRID(ST_MakePoint(?, ?), CAST(? AS integer)))
"
0.291 32.621 4326]
)














(seancorfield.readme/defreadme readme-377
(-> (select :*)
    (from :foo)
    (where [:< :expired_at [:raw ["now() - '" 5 " seconds'"]]])
    (sql/format {:foo 5}))
=> ["SELECT * FROM foo WHERE expired_at < now() - '? seconds'" 5]
)

(seancorfield.readme/defreadme readme-385
(-> (select :*)
    (from :foo)
    (where [:< :expired_at [:raw ["now() - '" [:param :t] " seconds'"]]])
    (sql/format {:t 5}))
=> ["SELECT * FROM foo WHERE expired_at < now() - '? seconds'" 5]
)










(seancorfield.readme/defreadme readme-402
(-> (select :foo.a)
    (from :foo)
    (where [:= :foo.a "baz"])
    (sql/format {:dialect :mysql}))
=> ["SELECT `foo`.`a` FROM `foo` WHERE `foo`.`a` = ?" "baz"]
)



















(seancorfield.readme/defreadme readme-428
(sql/format {:select [:*] :from :foo
             :where [:= :name [:inline "Jones"]]
             :lock [:in-share-mode]}
            {:dialect :mysql :quoted false})
=> ["SELECT * FROM foo WHERE name = 'Jones' LOCK IN SHARE MODE"]
)

(seancorfield.readme/defreadme readme-436
(-> (select :foo.a)
    (from :foo)
    (where [:= :foo.a "baz"])
    (for :update)
    (format))
=> ["SELECT foo.a FROM foo WHERE (foo.a = ?) FOR UPDATE" "baz"]
)




(seancorfield.readme/defreadme readme-448
(sql/format
  {:select [:f.foo-id :f.foo-name]
   :from [[:foo-bar :f]]
   :where [:= :f.foo-id 12345]}
  {:allow-dashed-names? true ; not implemented yet
   :quoted true})
=> ["SELECT \"f\".\"foo-id\", \"f\".\"foo-name\" FROM \"foo-bar\" \"f\" WHERE \"f\".\"foo-id\" = ?" 12345]
)





(seancorfield.readme/defreadme readme-462
(def big-complicated-map
  (-> (select :f.* :b.baz :c.quux [:b.bla "bla-bla"]
              [[:now]] [[:raw "@x := 10"]])
      #_(modifiers :distinct) ; this is not implemented yet
      (from [:foo :f] [:baz :b])
      (join :draq [:= :f.b :draq.x])
      (left-join [:clod :c] [:= :f.a :c.d])
      (right-join :bock [:= :bock.z :c.e])
      (where [:or
               [:and [:= :f.a "bort"] [:not= :b.baz [:param :param1]]]
               [:< 1 2 3]
               [:in :f.e [1 [:param :param2] 3]]
               [:between :f.e 10 20]])
      (group-by :f.a :c.e)
      (having [:< 0 :f.e])
      (order-by [:b.baz :desc] :c.quux [:f.a :nulls-first])
      (limit 50)
      (offset 10)))
)
(seancorfield.readme/defreadme readme-482
big-complicated-map
=> {:select [:f.* :b.baz :c.quux [:b.bla "bla-bla"]
             [[:now]] [[:raw "@x := 10"]]]
    :modifiers [:distinct]
    :from [[:foo :f] [:baz :b]]
    :join [:draq [:= :f.b :draq.x]]
    :left-join [[:clod :c] [:= :f.a :c.d]]
    :right-join [:bock [:= :bock.z :c.e]]
    :where [:or
             [:and [:= :f.a "bort"] [:not= :b.baz [:param :param1]]]
             [:< 1 2 3]
             [:in :f.e [1 [:param :param2] 3]]
             [:between :f.e 10 20]]
    :group-by [:f.a :c.e]
    :having [:< 0 :f.e]
    :order-by [[:b.baz :desc] :c.quux [:f.a :nulls-first]]
    :limit 50
    :offset 10}
)
(seancorfield.readme/defreadme readme-502
(sql/format big-complicated-map {:param1 "gabba" :param2 2})
=> ["
SELECT DISTINCT f.*, b.baz, c.quux, b.bla AS bla_bla, now(), @x := 10
FROM foo f, baz b
INNER JOIN draq ON f.b = draq.x
LEFT JOIN clod c ON f.a = c.d
RIGHT JOIN bock ON bock.z = c.e
WHERE ((f.a = ? AND b.baz <> ?) OR (? < ? AND ? < ?) OR (f.e in (?, ?, ?)) OR f.e BETWEEN ? AND ?)
GROUP BY f.a, c.e
HAVING ? < f.e
ORDER BY b.baz DESC, c.quux, f.a NULLS FIRST
LIMIT ?
OFFSET ?
"
"bort" "gabba" 1 2 2 3 1 2 3 10 20 0 50 10]
)
(seancorfield.readme/defreadme readme-519
;; Printable and readable
(= big-complicated-map (read-string (pr-str big-complicated-map)))
=> true
)







(seancorfield.readme/defreadme readme-531
(defmethod fmt/fn-handler "betwixt" [_ field lower upper]
  (str (fmt/to-sql field) " BETWIXT "
       (fmt/to-sql lower) " AND " (fmt/to-sql upper)))

(-> (select :a) (where [:betwixt :a 1 10]) sql/format)
=> ["SELECT a WHERE a BETWIXT ? AND ?" 1 10]
)



(seancorfield.readme/defreadme readme-542
;; Takes a MapEntry of the operator & clause data, plus the entire SQL map
(defmethod fmt/format-clause :foobar [[op v] sqlmap]
  (str "FOOBAR " (fmt/to-sql v)))
)
(seancorfield.readme/defreadme readme-547
(sql/format {:select [:a :b] :foobar :baz})
=> ["SELECT a, b FOOBAR baz"]
)
(seancorfield.readme/defreadme readme-551
(require '[honeysql.helpers :refer [defhelper]])

;; Defines a helper function, and allows 'build' to recognize your clause
(defhelper foobar [m args]
  (assoc m :foobar (first args)))
)
(seancorfield.readme/defreadme readme-558
(-> (select :a :b) (foobar :baz) sql/format)
=> ["SELECT a, b FOOBAR baz"]

)



(seancorfield.readme/defreadme readme-566
(fmt/register-clause! :foobar 110)
)









































