;; copyright (c) 2020-2024 sean corfield, all rights reserved

(ns honey.sql.xtdb-test
  (:require [clojure.test :refer [deftest is testing]]
            [honey.sql :as sql]
            [honey.sql.helpers :as h
             :refer [select exclude rename from]]))

(deftest select-tests
  (testing "select, exclude, rename"
    (is (= ["SELECT * EXCLUDE _id RENAME value AS foo_value FROM foo"]
           (sql/format (-> (select :*) (exclude :_id) (rename [:value :foo_value])
                           (from :foo)))))
    (is (= ["SELECT * EXCLUDE (_id, a) RENAME value AS foo_value FROM foo"]
           (sql/format (-> (select :*) (exclude :_id :a) (rename [:value :foo_value])
                           (from :foo)))))
    (is (= ["SELECT * EXCLUDE _id RENAME (value AS foo_value, a AS b) FROM foo"]
           (sql/format (-> (select :*) (exclude :_id)
                           (rename [:value :foo_value]
                                   [:a :b])
                           (from :foo)))))
    (is (= ["SELECT * EXCLUDE _id RENAME value AS foo_value, c.x FROM foo"]
           (sql/format (-> (select [:* (-> (exclude :_id) (rename [:value :foo_value]))]
                                   :c.x)
                           (from :foo)))))
    (is (= ["SELECT * EXCLUDE (_id, a) RENAME value AS foo_value, c.x FROM foo"]
           (sql/format (-> (select [:* (-> (exclude :_id :a) (rename [:value :foo_value]))]
                                   :c.x)
                           (from :foo)))))
    (is (= ["SELECT * EXCLUDE _id RENAME (value AS foo_value, a AS b), c.x FROM foo"]
           (sql/format (-> (select [:* (-> (exclude :_id)
                                           (rename [:value :foo_value]
                                                   [:a :b]))]
                                   :c.x)
                           (from :foo))))))
  (testing "select, nest_one, nest_many"
    (is (= ["SELECT a._id, NEST_ONE (SELECT * FROM foo AS b WHERE b_id = a._id) FROM bar AS a"]
           (sql/format '{select (a._id,
                                 ((nest_one {select * from ((foo b)) where (= b_id a._id)})))
                         from ((bar a))})))
    (is (= ["SELECT a._id, NEST_MANY (SELECT * FROM foo AS b) FROM bar AS a"]
           (sql/format '{select (a._id,
                                 ((nest_many {select * from ((foo b))})))
                         from ((bar a))})))))

(deftest dotted-array-access-tests
  (is (= ["SELECT (a.b).c"] ; old, partial support:
         (sql/format '{select (((. (nest :a.b) :c)))})))
  (is (= ["SELECT (a.b).c"] ; new, complete support:
         (sql/format '{select (((:get-in :a.b :c)))})))
  (is (= ["SELECT (a).b.c"] ; the first expression is always parenthesized:
         (sql/format '{select (((:get-in :a :b :c)))}))))

(deftest erase-from-test
  (is (= ["ERASE FROM foo WHERE foo.id = ?" 42]
         (-> {:erase-from :foo
              :where [:= :foo.id 42]}
             (sql/format))))
  (is (= ["ERASE FROM foo WHERE foo.id = ?" 42]
         (-> (h/erase-from :foo)
             (h/where [:= :foo.id 42])
             (sql/format)))))

(deftest inline-record-body
  (is (= ["{_id: 1, name: 'foo', info: {contact: [{loc: 'home', tel: '123'}, {loc: 'work', tel: '456'}]}}"]
         (sql/format [:inline {:_id 1 :name "foo"
                               :info {:contact [{:loc "home" :tel "123"}
                                                {:loc "work" :tel "456"}]}}]))))

(deftest records-statement
  (testing "auto-lift maps"
    (is (= ["RECORDS ?, ?" {:_id 1 :name "cat"} {:_id 2 :name "dog"}]
           (sql/format {:records [{:_id 1 :name "cat"}
                                  {:_id 2 :name "dog"}]}))))
  (testing "explicit inline"
    (is (= ["RECORDS {_id: 1, name: 'cat'}, {_id: 2, name: 'dog'}"]
           (sql/format {:records [[:inline {:_id 1 :name "cat"}]
                                  [:inline {:_id 2 :name "dog"}]]}))))
  (testing "insert with records"
    (is (= ["INSERT INTO foo RECORDS {_id: 1, name: 'cat'}, {_id: 2, name: 'dog'}"]
           (sql/format {:insert-into :foo
                        :records [[:inline {:_id 1 :name "cat"}]
                                  [:inline {:_id 2 :name "dog"}]]})))
    (is (= ["INSERT INTO foo RECORDS {_id: 1, name: 'cat'}, {_id: 2, name: 'dog'}"]
           (sql/format {:insert-into :foo
                        :records [[:inline {:_id 1 :name "cat"}]
                                  [:inline {:_id 2 :name "dog"}]]})))
    (is (= ["INSERT INTO foo RECORDS ?, ?" {:_id 1 :name "cat"} {:_id 2 :name "dog"}]
           (sql/format {:insert-into [:foo ; as a sub-clause
                                      {:records [{:_id 1 :name "cat"}
                                                 {:_id 2 :name "dog"}]}]})))))

(deftest patch-statement
  (testing "patch with records"
    (is (= ["PATCH INTO foo RECORDS {_id: 1, name: 'cat'}, {_id: 2, name: 'dog'}"]
           (sql/format {:patch-into [:foo]
                        :records [[:inline {:_id 1 :name "cat"}]
                                  [:inline {:_id 2 :name "dog"}]]})))
    (is (= ["PATCH INTO foo RECORDS ?, ?" {:_id 1 :name "cat"} {:_id 2 :name "dog"}]
           (sql/format {:patch-into [:foo ; as a sub-clause
                                     {:records [{:_id 1 :name "cat"}
                                                {:_id 2 :name "dog"}]}]})))
    (is (= ["PATCH INTO foo RECORDS ?, ?" {:_id 1 :name "cat"} {:_id 2 :name "dog"}]
           (sql/format (h/patch-into :foo
                                     (h/records [{:_id 1 :name "cat"}
                                                 {:_id 2 :name "dog"}])))))))

(deftest object-record-expr
  (testing "object literal"
    (is (= ["SELECT OBJECT (_id: 1, name: 'foo')"]
           (sql/format {:select [[[:object {:_id 1 :name "foo"}]]]})))
    (is (= ["SELECT OBJECT (_id: 1, name: 'foo')"]
           (sql/format '{select (((:object {:_id 1 :name "foo"})))}))))
  (testing "record literal"
    (is (= ["SELECT RECORD (_id: 1, name: 'foo')"]
           (sql/format {:select [[[:record {:_id 1 :name "foo"}]]]})))
    (is (= ["SELECT RECORD (_id: 1, name: 'foo')"]
           (sql/format '{select (((:record {:_id 1 :name "foo"})))}))))
  (testing "inline map literal"
    (is (= ["SELECT {_id: 1, name: 'foo'}"]
           (sql/format {:select [[[:inline {:_id 1 :name "foo"}]]]})))))

(deftest navigation-dot-index
  (is (= ["SELECT (a.b).c[1].d"]
         (sql/format '{select (((get-in a.b c 1 d)))})))
  (is (= ["SELECT (a.b).c[?].d" 1]
         (sql/format '{select (((get-in a.b c (lift 1) d)))})))
  (is (= ["SELECT (a.b).c[?].d" 1]
         (sql/format '{select (((get-in (. a b) c (lift 1) d)))})))
  (is (= ["SELECT (OBJECT (_id: 1, b: 'thing').b).c[?].d" 1]
         (sql/format '{select (((get-in (. (object {_id 1 b "thing"}) b) c (lift 1) d)))}))))
