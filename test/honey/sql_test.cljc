;; copyright (c) 2021-2025 sean corfield, all rights reserved

(ns honey.sql-test
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [honey.sql :as sut :refer [format]]
            [honey.sql.helpers :as h])
  #?(:clj (:import (clojure.lang ExceptionInfo)
                   (java.net URLEncoder))))

(deftest mysql-tests
  (is (= ["SELECT * FROM `table` WHERE `id` = ?" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                     {:dialect :mysql}))))

(deftest expr-tests
  ;; special-cased = nil:
  (is (= ["id IS NULL"]
         (sut/format-expr [:= :id nil])))
  (is (= ["id IS NULL"]
         (sut/format-expr [:is :id nil])))
  (is (= ["id = TRUE"]
         (sut/format-expr [:= :id true])))
  (is (= ["[id] = ?" true]
         (sut/format [:= :id true] {:dialect :sqlserver})))
  (is (= ["id IS TRUE"]
         (sut/format-expr [:is :id true])))
  (is (= ["id <> TRUE"]
         (sut/format-expr [:<> :id true])))
  (is (= ["id IS NOT TRUE"]
         (sut/format-expr [:is-not :id true])))
  (is (= ["id = FALSE"]
         (sut/format-expr [:= :id false])))
  (is (= ["id IS FALSE"]
         (sut/format-expr [:is :id false])))
  (is (= ["id <> FALSE"]
         (sut/format-expr [:<> :id false])))
  (is (= ["id IS NOT FALSE"]
         (sut/format-expr [:is-not :id false])))
  ;; special-cased <> nil:
  (is (= ["id IS NOT NULL"]
         (sut/format-expr [:<> :id nil])))
  ;; legacy alias:
  (is (= ["id IS NOT NULL"]
         (sut/format-expr [:!= :id nil])))
  ;; legacy alias:
  (is (= ["id IS NOT NULL"]
         (sut/format-expr [:not= :id nil])))
  (is (= ["id IS NOT NULL"]
         (sut/format-expr [:is-not :id nil])))
  ;; degenerate (special) cases:
  (is (= ["NULL IS NULL"]
         (sut/format-expr [:= nil nil])))
  (is (= ["NULL IS NOT NULL"]
         (sut/format-expr [:<> nil nil])))
  (is (= ["id = ?" 1]
         (sut/format-expr [:= :id 1])))
  (is (= ["id + ?" 1]
         (sut/format-expr [:+ :id 1])))
  (is (= ["? + (? + quux)" 1 1]
         (sut/format-expr [:+ 1 [:+ 1 :quux]])))
  (is (= ["? + ? + quux" 1 1]
         (sut/format-expr [:+ 1 1 :quux])))
  (is (= ["FOO(BAR(? + G(abc)), F(?, quux))" 2 1]
         (sut/format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])))
  (is (= ["id"]
         (sut/format-expr :id)))
  (is (= ["?" 1]
         (sut/format-expr 1)))
  (is (= ["INTERVAL ? DAYS" 30]
         (sut/format-expr [:interval 30 :days]))))

(deftest issue-486-interval
  (is (= ["INTERVAL '30 Days'"]
         (sut/format-expr [:interval "30 Days"]))))

(deftest issue-455-null
  (is (= ["WHERE (abc + ?) IS NULL" "abc"]
         (sut/format {:where [:= [:+ :abc "abc"] nil]}))))

(deftest where-test
  (is (= ["WHERE id = ?" 1]
         (#'sut/format-on-expr :where [:= :id 1]))))

(deftest general-tests
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" = ?" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" = ?" 1]
         (sut/format {:select [:*] :from [:table] :where (sut/map= {:id 1})} {:quoted true})))
  (is (= ["SELECT \"t\".* FROM \"table\" AS \"t\" WHERE \"id\" = ?" 1]
         (sut/format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" GROUP BY \"foo\""]
         (sut/format {:select [:*] :from [:table] :group-by :foo} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" GROUP BY \"foo\", \"bar\""]
         (sut/format {:select [:*] :from [:table] :group-by [:foo :bar]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" GROUP BY DATE(\"bar\")"]
         (sut/format {:select [:*] :from [:table] :group-by [[:date :bar]]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" ORDER BY \"foo\" ASC"]
         (sut/format {:select [:*] :from [:table] :order-by :foo} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" ORDER BY \"foo\" DESC, \"bar\" ASC"]
         (sut/format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" ORDER BY DATE(\"expiry\") DESC, \"bar\" ASC"]
         (sut/format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" WHERE DATE_ADD(\"expiry\", INTERVAL ? DAYS) < NOW()" 30]
         (sut/format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]} {:quoted true})))
  (is (= ["SELECT * FROM \"table\" WHERE DATE_ADD(\"expiry\", INTERVAL '30 Days') < NOW()"]
         (sut/format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval "30 Days"]] [:now]]} {:quoted true})))
  (is (= ["SELECT * FROM `table` WHERE `id` = ?" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})))
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" IN (?, ?, ?, ?)" 1 2 3 4]
         (sut/format {:select [:*] :from [:table] :where [:in :id [1 2 3 4]]} {:quoted true}))))

(deftest general-numbered-tests
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" = $1" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" = $1" 1]
         (sut/format {:select [:*] :from [:table] :where (sut/map= {:id 1})}
                     {:quoted true :numbered true})))
  (is (= ["SELECT \"t\".* FROM \"table\" AS \"t\" WHERE \"id\" = $1" 1]
         (sut/format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" GROUP BY \"foo\", \"bar\""]
         (sut/format {:select [:*] :from [:table] :group-by [:foo :bar]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" GROUP BY DATE(\"bar\")"]
         (sut/format {:select [:*] :from [:table] :group-by [[:date :bar]]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" ORDER BY \"foo\" DESC, \"bar\" ASC"]
         (sut/format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" ORDER BY DATE(\"expiry\") DESC, \"bar\" ASC"]
         (sut/format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" WHERE DATE_ADD(\"expiry\", INTERVAL $1 DAYS) < NOW()" 30]
         (sut/format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM \"table\" WHERE DATE_ADD(\"expiry\", INTERVAL '30 Days') < NOW()"]
         (sut/format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval "30 Days"]] [:now]]}
                     {:quoted true :numbered true})))
  (is (= ["SELECT * FROM `table` WHERE `id` = $1" 1]
         (sut/format {:select [:*] :from [:table] :where [:= :id 1]}
                     {:dialect :mysql :numbered true})))
  (is (= ["SELECT * FROM \"table\" WHERE \"id\" IN ($1, $2, $3, $4)" 1 2 3 4]
         (sut/format {:select [:*] :from [:table] :where [:in :id [1 2 3 4]]}
                     {:quoted true :numbered true}))))

;; issue-based tests

(deftest subquery-alias-263
  (is (= ["SELECT type FROM (SELECT address AS field_alias FROM Candidate) AS sub_q_alias"]
         (sut/format {:select [:type]
                      :from [[{:select [[:address :field-alias]]
                               :from [:Candidate]} :sub_q_alias]]})))
  (is (= ["SELECT type FROM (SELECT address field_alias FROM Candidate) sub_q_alias"]
         (sut/format {:select [:type]
                      :from [[{:select [[:address :field-alias]]
                               :from [:Candidate]} :sub-q-alias]]}
                     {:dialect :oracle :quoted false}))))

;; tests lifted from HoneySQL 1.x to check for compatibility

(deftest alias-splitting
  (is (= ["SELECT `aa`.`c` AS `a.c`, `bb`.`c` AS `b.c`, `cc`.`c` AS `c.c`"]
         (format {:select [[:aa.c "a.c"]
                           [:bb.c :b.c]
                           [:cc.c 'c.c]]}
                 {:dialect :mysql}))
      "aliases containing \".\" are quoted as necessary but not split"))

(deftest values-alias
  (is (= ["SELECT vals.a FROM (VALUES (?, ?, ?)) AS vals (a, b, c)" 1 2 3]
         (format {:select [:vals.a]
                  :from [[{:values [[1 2 3]]} [:vals {:columns [:a :b :c]}]]]}))))
(deftest test-cte
  (is (= (format {:with [[:query {:select [:foo] :from [:bar]}]]})
         ["WITH query AS (SELECT foo FROM bar)"]))
  (is (= (format {:with [[:query {:select [:foo] :from [:bar]} :materialized]]})
         ["WITH query AS MATERIALIZED (SELECT foo FROM bar)"]))
  (is (= (format {:with [[:query {:select [:foo] :from [:bar]} :not-materialized]]})
         ["WITH query AS NOT MATERIALIZED (SELECT foo FROM bar)"]))
  (is (= (format {:with [[:query {:select [:foo] :from [:bar]} :kw-1 :kw-2]]})
         ["WITH query AS (SELECT foo FROM bar) KW 1 kw_2"]))
  (is (= (format {:with-recursive [[:query {:select [:foo] :from [:bar]} :cycle [:a :b :c] :set :d :to [:abs :e] :default 42 :using :x]]})
         ["WITH RECURSIVE query AS (SELECT foo FROM bar) CYCLE a, b, c SET d TO ABS(e) DEFAULT ? USING x" 42]))
  (is (= (format {:with [[:query1 {:select [:foo] :from [:bar]}]
                         [:query2 {:select [:bar] :from [:quux]}]]
                  :select [:query1.id :query2.name]
                  :from [:query1 :query2]})
         ["WITH query1 AS (SELECT foo FROM bar), query2 AS (SELECT bar FROM quux) SELECT query1.id, query2.name FROM query1, query2"]))
  (is (= (format {:with-recursive [[:query {:select [:foo] :from [:bar]}]]})
         ["WITH RECURSIVE query AS (SELECT foo FROM bar)"]))
  (is (= (format {:with [[[:static {:columns [:a :b :c]}] {:values [[1 2 3] [4 5]]}]]})
         ["WITH static (a, b, c) AS (VALUES (?, ?, ?), (?, ?, NULL))" 1 2 3 4 5]))
  (is (= (format
          {:with [[[:static {:columns [:a :b :c]}]
                   {:values [[1 2] [4 5 6]]}]]
           :select [:*]
           :from [:static]})
         ["WITH static (a, b, c) AS (VALUES (?, ?, NULL), (?, ?, ?)) SELECT * FROM static" 1 2 4 5 6]))
  (testing "When the expression passed to WITH clause is a string or `ident?` the syntax of WITH clause is `with expr AS ident`"
    (is (= (format
             {:with   [[:ts_upper_bound "2019-08-01 15:23:00"]]
              :select [:*]
              :from   [:hits]
              :where  [:= :EventDate :ts_upper_bound]})
           ["WITH ? AS ts_upper_bound SELECT * FROM hits WHERE EventDate = ts_upper_bound" "2019-08-01 15:23:00"]))
    (is (= (format
             {:with   [[:ts_upper_bound :2019-08-01]]
              :select [:*]
              :from   [:hits]
              :where  [:= :EventDate :ts_upper_bound]})
           ["WITH 2019_08_01 AS ts_upper_bound SELECT * FROM hits WHERE EventDate = ts_upper_bound"])))
  (testing "Mixing the syntax of WITH in the resulting clause"
    (is (= (format
             {:with   [[:ts_upper_bound "2019-08-01 15:23:00"]
                       [:stuff {:select [:*]
                                :from [:songs]}]]
              :select [:*]
              :from   [:hits :stuff]
              :where  [:= :EventDate :ts_upper_bound]})
           ["WITH ? AS ts_upper_bound, stuff AS (SELECT * FROM songs) SELECT * FROM hits, stuff WHERE EventDate = ts_upper_bound"
            "2019-08-01 15:23:00"])))
  (testing "Use expression in a WITH clause"
    (is (= (format
            {:with     [[:s [:sum :bytes]]]
             :select   [:s]
             :from     [:table]})
           ["WITH SUM(bytes) AS s SELECT s FROM table"]))

    (is (= (format
            {:with   [[:v [:raw "m['k']"]]]
             :select [:v]
             :from   [:table]})
           ["WITH m['k'] AS v SELECT v FROM table"]))

    (is (= (format
            {:with   [[:cond [:and [:= :a 1] [:= :b 2] [:= :c 3]]]]
             :select [:v]
             :from   [:table]
             :where  :cond})
           ["WITH (a = ?) AND (b = ?) AND (c = ?) AS cond SELECT v FROM table WHERE cond" 1 2 3]))))

(deftest insert-into
  (is (= (format {:insert-into :foo})
         ["INSERT INTO foo"]))
  (is (= (format {:insert-into [:foo {:select [:bar] :from [:baz]}]})
         ["INSERT INTO foo SELECT bar FROM baz"]))
  (is (= (format {:insert-into [[:foo [:a :b :c]] {:select [:d :e :f] :from [:baz]}]})
         ["INSERT INTO foo (a, b, c) SELECT d, e, f FROM baz"]))
  (is (= (format {:insert-into [[:foo [:a :b :c]] {:select [:d :e :f] :from [:baz]}]})
         ["INSERT INTO foo (a, b, c) SELECT d, e, f FROM baz"])))

(deftest insert-into-namespaced
  ;; un-namespaced: works as expected:
  (is (= (format {:insert-into :foo :values [{:foo/id 1}]})
         ["INSERT INTO foo (id) VALUES (?)" 1]))
  (is (= (format {:insert-into :foo :columns [:foo/id] :values [[2]]})
         ["INSERT INTO foo (id) VALUES (?)" 2]))
  (is (= (format {:insert-into :foo :values [{:foo/id 1}]}
                 {:namespace-as-table? true})
         ["INSERT INTO foo (id) VALUES (?)" 1]))
  (is (= (format {:insert-into :foo :columns [:foo/id] :values [[2]]}
                 {:namespace-as-table? true})
         ["INSERT INTO foo (id) VALUES (?)" 2])))

(deftest insert-into-uneven-maps
  ;; we can't rely on ordering when the set of keys differs between maps:
  (let [res (format {:insert-into :foo :values [{:id 1} {:id 2, :bar "quux"}]})]
    (is (or (= res ["INSERT INTO foo (id, bar) VALUES (?, NULL), (?, ?)" 1 2 "quux"])
            (= res ["INSERT INTO foo (bar, id) VALUES (NULL, ?), (?, ?)" 1 "quux" 2]))))
  (let [res (format {:insert-into :foo :values [{:id 1, :bar "quux"} {:id 2}]})]
    (is (or (= res ["INSERT INTO foo (id, bar) VALUES (?, ?), (?, NULL)" 1 "quux" 2])
            (= res ["INSERT INTO foo (bar, id) VALUES (?, ?), (NULL, ?)" "quux" 1 2])))))

(deftest insert-into-functions
  ;; needs [[:raw ..]] because it's the columns case:
  (is (= (format {:insert-into [[[:raw "My-Table Name"]] {:select [:bar] :from [:baz]}]})
         ["INSERT INTO My-Table Name SELECT bar FROM baz"]))
  ;; this variant only needs [:raw ..]
  (is (= (format {:insert-into [[:raw "My-Table Name"]] :values [{:foo/id 1}]})
         ["INSERT INTO My-Table Name (id) VALUES (?)" 1]))
  (is (= (format {:insert-into [:foo :bar] :values [{:foo/id 1}]})
         ["INSERT INTO foo AS bar (id) VALUES (?)" 1])))

(deftest exists-test
  ;; EXISTS should never have been implemented as SQL syntax: it's an operator!
  #_(is (= (format {:exists {:select [:a] :from [:foo]}})
           ["EXISTS (SELECT a FROM foo)"]))
  ;; select function call with an alias:
  (is (= (format {:select [[[:exists {:select [:a] :from [:foo]}] :x]]})
         ["SELECT EXISTS (SELECT a FROM foo) AS x"]))
  ;; select function call with no alias required:
  (is (= (format {:select [[[:exists {:select [:a] :from [:foo]}]]]})
         ["SELECT EXISTS (SELECT a FROM foo)"]))
  (is (= (format {:select [:id]
                  :from [:foo]
                  :where [:exists {:select [1]
                                   :from [:bar]
                                   :where :deleted}]})
         ["SELECT id FROM foo WHERE EXISTS (SELECT ? FROM bar WHERE deleted)" 1])))

(deftest array-test
  (is (= (format {:insert-into :foo
                  :columns [:baz]
                  :values [[[:array [1 2 3 4]]]]})
         ["INSERT INTO foo (baz) VALUES (ARRAY[?, ?, ?, ?])" 1 2 3 4]))
  (is (= (format {:insert-into :foo
                  :columns [:baz]
                  :values [[[:array ["one" "two" "three"]]]]})
         ["INSERT INTO foo (baz) VALUES (ARRAY[?, ?, ?])" "one" "two" "three"]))
  #_ ;; requested feature -- does not work yet
  (is (= (format {:insert-into :foo
                  :columns [:baz]
                  :values [[[:array :?vals]]]}
                 {:params {:vals [1 2 3 4]}})
         ["INSERT INTO foo (baz) VALUES (ARRAY[?, ?, ?, ?])" 1 2 3 4]))
  (testing "typed array"
    (is (= (format {:select [[[:array [] :integer]]]})
           ["SELECT ARRAY[]::INTEGER[]"]))
    (is (= (format {:select [[[:array [1 2] :text]]]})
           ["SELECT ARRAY[?, ?]::TEXT[]" 1 2])))
  (testing "array subquery"
    (is (= (format {:select [[[:array {:select [:foo] :from [:bar]}]]]})
           ["SELECT ARRAY(SELECT foo FROM bar)"]))
    (is (= (format {:select [[[:array {:select ^{:as :struct} [:foo :bar] :from [:bar]}]]]})
           ["SELECT ARRAY(SELECT AS STRUCT foo, bar FROM bar)"]))
    ;; documented subquery workaround:
    (is (= (format {:select [[[:'ARRAY {:select [:foo] :from [:bar]}]]]})
           ["SELECT ARRAY (SELECT foo FROM bar)"]))))

(deftest union-test
  ;; UNION and INTERSECT subexpressions should not be parenthesized.
  ;; If you need to add more complex expressions, use a subquery like this:
  ;;   SELECT foo FROM bar1
  ;;   UNION
  ;;   SELECT foo FROM (SELECT foo FROM bar2 ORDER BY baz LIMIT 2)
  ;;   ORDER BY foo ASC
  (is (= (format {:union [{:select [:foo] :from [:bar1]}
                          {:select [:foo] :from [:bar2]}]})
         ["SELECT foo FROM bar1 UNION SELECT foo FROM bar2"]))

  (testing "union complex values"
    (is (= (format {:union [{:select [:foo] :from [:bar1]}
                            {:select [:foo] :from [:bar2]}]
                    :with [[[:bar {:columns [:spam :eggs]}]
                            {:values [[1 2] [3 4] [5 6]]}]]})
           ["WITH bar (spam, eggs) AS (VALUES (?, ?), (?, ?), (?, ?)) SELECT foo FROM bar1 UNION SELECT foo FROM bar2"
            1 2 3 4 5 6]))))

(deftest union-all-test
  (is (= (format {:union-all [{:select [:foo] :from [:bar1]}
                              {:select [:foo] :from [:bar2]}]})
         ["SELECT foo FROM bar1 UNION ALL SELECT foo FROM bar2"])))

(deftest intersect-test
  (is (= (format {:intersect [{:select [:foo] :from [:bar1]}
                              {:select [:foo] :from [:bar2]}]})
         ["SELECT foo FROM bar1 INTERSECT SELECT foo FROM bar2"])))

(deftest except-test
  (is (= (format {:except [{:select [:foo] :from [:bar1]}
                           {:select [:foo] :from [:bar2]}]})
         ["SELECT foo FROM bar1 EXCEPT SELECT foo FROM bar2"])))

(deftest inner-parts-test
  (testing "The correct way to apply ORDER BY to various parts of a UNION"
    (is (= (format
            {:union
             [{:select [:amount :id :created_on]
               :from [:transactions]}
              {:select [:amount :id :created_on]
               :from [{:select [:amount :id :created_on]
                       :from [:other_transactions]
                       :order-by [[:amount :desc]]
                       :limit 5}]}]
             :order-by [[:amount :asc]]})
           ["SELECT amount, id, created_on FROM transactions UNION SELECT amount, id, created_on FROM (SELECT amount, id, created_on FROM other_transactions ORDER BY amount DESC LIMIT ?) ORDER BY amount ASC" 5]))))

(deftest compare-expressions-test
  (testing "Sequences should be fns when in value/comparison spots"
    (is (= ["SELECT foo FROM bar WHERE (col1 % ?) = (col2 + ?)" 4 4]
           (format {:select [:foo]
                    :from [:bar]
                    :where [:= [:% :col1 4] [:+ :col2 4]]})))
    (is (= ["SELECT foo FROM bar WHERE MOD(col1, ?) = (col2 + ?)" 4 4]
           (format {:select [:foo]
                    :from [:bar]
                    :where [:= [:mod :col1 4] [:+ :col2 4]]}))))

  (testing "Example from dharrigan"
    (is (= ["SELECT PG_TRY_ADVISORY_LOCK(1)"]
           (format {:select [:%pg_try_advisory_lock.1]}))))

  (testing "Value context only applies to sequences in value/comparison spots"
    (let [sub {:select [:%sum.amount]
               :from [:bar]
               :where [:in :id ["id-1" "id-2"]]}]
      (is (= ["SELECT total FROM foo WHERE (SELECT SUM(amount) FROM bar WHERE id IN (?, ?)) = total" "id-1" "id-2"]
             (format {:select [:total]
                      :from [:foo]
                      :where [:= sub :total]})))
      (is (= ["WITH t AS (SELECT SUM(amount) FROM bar WHERE id IN (?, ?)) SELECT total FROM foo WHERE total = t" "id-1" "id-2"]
             (format {:with [[:t sub]]
                      :select [:total]
                      :from [:foo]
                      :where [:= :total :t]}))))))

(deftest union-with-cte
  (is (= (format {:union [{:select [:foo] :from [:bar1]}
                          {:select [:foo] :from [:bar2]}]
                  :with [[[:bar {:columns [:spam :eggs]}]
                          {:values [[1 2] [3 4] [5 6]]}]]})
         ["WITH bar (spam, eggs) AS (VALUES (?, ?), (?, ?), (?, ?)) SELECT foo FROM bar1 UNION SELECT foo FROM bar2" 1 2 3 4 5 6])))

(deftest union-all-with-cte
  (is (= (format {:union-all [{:select [:foo] :from [:bar1]}
                              {:select [:foo] :from [:bar2]}]
                  :with [[[:bar {:columns [:spam :eggs]}]
                          {:values [[1 2] [3 4] [5 6]]}]]})
         ["WITH bar (spam, eggs) AS (VALUES (?, ?), (?, ?), (?, ?)) SELECT foo FROM bar1 UNION ALL SELECT foo FROM bar2" 1 2 3 4 5 6])))

(deftest parameterizer-none
  (testing "array parameter"
    (is (= (format {:insert-into :foo
                    :columns [:baz]
                    :values [[[:array [1 2 3 4]]]]}
                   {:inline true})
           ["INSERT INTO foo (baz) VALUES (ARRAY[1, 2, 3, 4])"])))

  (testing "union complex values -- fail: parameterizer"
    (is (= (format {:union [{:select [:foo] :from [:bar1]}
                            {:select [:foo] :from [:bar2]}]
                    :with [[[:bar {:columns [:spam :eggs]}]
                            {:values [[1 2] [3 4] [5 6]]}]]}
                   {:inline true})
           ["WITH bar (spam, eggs) AS (VALUES (1, 2), (3, 4), (5, 6)) SELECT foo FROM bar1 UNION SELECT foo FROM bar2"]))))

(deftest inline-was-parameterizer-none
  (testing "array parameter"
    (is (= (format {:insert-into :foo
                    :columns [:baz]
                    :values [[[:array (mapv vector
                                            (repeat :inline)
                                            [1 2 3 4])]]]})
           ["INSERT INTO foo (baz) VALUES (ARRAY[1, 2, 3, 4])"])))

  (testing "union complex values"
    (is (= (format {:union [{:select [:foo] :from [:bar1]}
                            {:select [:foo] :from [:bar2]}]
                    :with [[[:bar {:columns [:spam :eggs]}]
                            {:values (mapv #(mapv vector (repeat :inline) %)
                                           [[1 2] [3 4] [5 6]])}]]})
           ["WITH bar (spam, eggs) AS (VALUES (1, 2), (3, 4), (5, 6)) SELECT foo FROM bar1 UNION SELECT foo FROM bar2"]))))

(deftest similar-regex-tests
  (testing "basic similar to"
    (is (= (format {:select :* :from :foo
                    :where [:similar-to :foo [:escape "bar" [:inline  "*"]]]})
           ["SELECT * FROM foo WHERE foo SIMILAR TO ? ESCAPE '*'" "bar"]))))

(deftest former-parameterizer-tests-where-and
  ;; I have no plans for positional parameters -- I just don't see the point
  #_(testing "should ignore a nil predicate -- fail: postgresql parameterizer"
      (is (= (format {:where [:and
                              [:= :foo "foo"]
                              [:= :bar "bar"]
                              nil
                              [:= :quux "quux"]]}
                     {:parameterizer :postgresql})
             ["WHERE (foo = ?) AND (bar = $2) AND (quux = $3)" "foo" "bar" "quux"])))
  ;; new :inline option is similar to :parameterizer :none in 1.x
  (testing "should fill param with single quote"
    (is (= (format {:where [:and
                            [:= :foo "foo"]
                            [:= :bar "bar"]
                            nil
                            [:= :quux "quux"]]}
                   {:inline true})
           ["WHERE (foo = 'foo') AND (bar = 'bar') AND (quux = 'quux')"])))
  (testing "should inline params with single quote"
    (is (= (format {:where [:and
                            [:= :foo [:inline "foo"]]
                            [:= :bar [:inline "bar"]]
                            nil
                            [:= :quux [:inline "quux"]]]})
           ["WHERE (foo = 'foo') AND (bar = 'bar') AND (quux = 'quux')"])))
  ;; this is the normal behavior -- not a custom parameterizer!
  (testing "should fill param with ?"
    (is (= (format {:where [:and
                            [:= :foo "foo"]
                            [:= :bar "bar"]
                            nil
                            [:= :quux "quux"]]}
                   ;; this never did anything useful:
                   #_{:parameterizer :mysql-fill})
           ["WHERE (foo = ?) AND (bar = ?) AND (quux = ?)" "foo" "bar" "quux"]))))

#?(:clj
   (deftest issue-385-test
     (let [u (java.util.UUID/randomUUID)]
       (is (= [(str "VALUES ('" (str u) "')")]
              (format {:values [[u]]} {:inline true}))))))

(deftest set-before-from
  ;; issue 235
  (is (=
       ["UPDATE \"films\" \"f\" SET \"kind\" = \"c\".\"test\" FROM (SELECT \"b\".\"test\" FROM \"bar\" AS \"b\" WHERE \"b\".\"id\" = ?) AS \"c\" WHERE \"f\".\"kind\" = ?" 1 "drama"]
       (->
        {:update [:films :f]
         :set    {:kind :c.test}
         :from   [[{:select [:b.test]
                    :from   [[:bar :b]]
                    :where  [:= :b.id 1]} :c]]
         :where  [:= :f.kind "drama"]}
        (format {:quoted true}))))
  ;; issue 317
  (is (=
       ["UPDATE \"films\" \"f\" SET \"kind\" = \"c\".\"test\" FROM (SELECT \"b\".\"test\" FROM \"bar\" AS \"b\" WHERE \"b\".\"id\" = ?) AS \"c\" WHERE \"f\".\"kind\" = ?" 1 "drama"]
       (->
        {:update [:films :f]
         ;; drop ns in set clause...
         :set    {:f/kind :c.test}
         :from   [[{:select [:b.test]
                    :from   [[:bar :b]]
                    :where  [:= :b.id 1]} :c]]
         :where  [:= :f.kind "drama"]}
        (format {:quoted true}))))
  (is (=
       ["UPDATE \"films\" \"f\" SET \"f\".\"kind\" = \"c\".\"test\" FROM (SELECT \"b\".\"test\" FROM \"bar\" AS \"b\" WHERE \"b\".\"id\" = ?) AS \"c\" WHERE \"f\".\"kind\" = ?" 1 "drama"]
       (->
        {:update [:films :f]
         ;; ...but keep literal dotted name
         :set    {:f.kind :c.test}
         :from   [[{:select [:b.test]
                    :from   [[:bar :b]]
                    :where  [:= :b.id 1]} :c]]
         :where  [:= :f.kind "drama"]}
        (format {:quoted true})))))

(deftest set-after-join
  (is (=
       ["UPDATE `foo` INNER JOIN `bar` ON `bar`.`id` = `foo`.`bar_id` SET `a` = ? WHERE `bar`.`b` = ?" 1 42]
       (->
        {:update :foo
         :join   [:bar [:= :bar.id :foo.bar_id]]
         :set    {:a 1}
         :where  [:= :bar.b 42]}
        (format {:dialect :mysql}))))
  ;; issue 344
  (is (=
       ["UPDATE `foo` INNER JOIN `bar` ON `bar`.`id` = `foo`.`bar_id` SET `f`.`a` = ? WHERE `bar`.`b` = ?" 1 42]
       (->
        {:update :foo
         :join   [:bar [:= :bar.id :foo.bar_id]]
         ;; do not drop ns in set clause for MySQL:
         :set    {:f/a 1}
         :where  [:= :bar.b 42]}
        (format {:dialect :mysql})))))

(deftest format-arity-test
  (testing "format can be called with no options"
    (is (= ["DELETE FROM foo WHERE foo.id = ?" 42]
           (-> {:delete-from :foo
                :where [:= :foo.id 42]}
               (format)))))
  (testing "format can be called with an options hash map"
    (is (= ["\nDELETE FROM `foo`\nWHERE `foo`.`id` = ?\n" 42]
           (-> {:delete-from :foo
                :where [:= :foo.id 42]}
               (format {:dialect :mysql :pretty true})))))
  (testing "format can be called with named arguments"
    (is (= ["\nDELETE FROM `foo`\nWHERE `foo`.`id` = ?\n" 42]
           (-> {:delete-from :foo
                :where [:= :foo.id 42]}
               (format :dialect :mysql :pretty true)))))
  (let [version #?(:cljs *clojurescript-version*
                   :default (clojure-version))]
    (when (or (str/starts-with? version "1.12")
              (str/starts-with? version "1.11"))
      (testing "format can be called with mixed arguments"
        (is (= ["\nDELETE FROM `foo`\nWHERE `foo`.`id` = ?\n" 42]
               (-> {:delete-from :foo
                    :where [:= :foo.id 42]}
                   (format :dialect :mysql {:pretty true}))))))))

(deftest delete-from-test
  (is (= ["DELETE FROM `foo` WHERE `foo`.`id` = ?" 42]
         (-> {:delete-from :foo
              :where [:= :foo.id 42]}
             (format {:dialect :mysql})))))

(deftest delete-test
  (is (= ["DELETE `t1`, `t2` FROM `table1` AS `t1` INNER JOIN `table2` AS `t2` ON `t1`.`fk` = `t2`.`id` WHERE `t1`.`bar` = ?" 42]
         (-> {:delete [:t1 :t2]
              :from [[:table1 :t1]]
              :join [[:table2 :t2] [:= :t1.fk :t2.id]]
              :where [:= :t1.bar 42]}
             (format {:dialect :mysql})))))

(deftest delete-using
  (is (= ["DELETE FROM films USING producers WHERE (producer_id = producers.id) AND (producers.name = ?)" "foo"]
         (-> {:delete-from :films
              :using [:producers]
              :where [:and
                      [:= :producer_id :producers.id]
                      [:= :producers.name "foo"]]}
             (format)))))

(deftest truncate-test
  (is (= ["TRUNCATE TABLE `foo`"]
         (-> {:truncate :foo}
             (format {:dialect :mysql}))))
  (is (= ["TRUNCATE TABLE `foo` CONTINUE IDENTITY"]
         (-> {:truncate [:foo :continue :identity]}
             (format {:dialect :mysql}))))
  (is (= ["TRUNCATE TABLE `t1`, `t2`"]
         (-> {:truncate [[:t1 :t2]]}
             (format {:dialect :mysql}))))
  (is (= ["TRUNCATE TABLE `t1`, `t2` CONTINUE IDENTITY"]
         (-> {:truncate [[:t1 :t2] :continue :identity]}
             (format {:dialect :mysql})))))

(deftest inlined-values-are-stringified-correctly
  (is (= ["SELECT 'foo', 'It''s a quote!', bar, NULL"]
         (format {:select [[[:inline "foo"]]
                           [[:inline "It's a quote!"]]
                           [[:inline :bar]]
                           [[:inline nil]]]}))))

;; Make sure if Locale is Turkish we're not generating queries like İNNER JOIN (dot over the I) because
;; `string/upper-case` is converting things to upper-case using the default Locale. Generated query should be the same
;; regardless of system Locale. See #236
#?(:clj
   (deftest statements-generated-correctly-with-turkish-locale
     (let [format-with-locale (fn [^String language-tag]
                                (let [original-locale (java.util.Locale/getDefault)]
                                  (try
                                    (java.util.Locale/setDefault (java.util.Locale/forLanguageTag language-tag))
                                    (format {:select [:t2.name]
                                             :from   [[:table1 :t1]]
                                             :join   [[:table2 :t2] [:= :t1.fk :t2.id]]
                                             :where  [:= :t1.id 1]})
                                    (finally
                                      (java.util.Locale/setDefault original-locale)))))]
       (is (= (format-with-locale "en")
              (format-with-locale "tr"))))))

(deftest join-on-true-253
  ;; used to work on honeysql 0.9.2; broke in 0.9.3
  (is (= ["SELECT foo FROM bar INNER JOIN table AS t ON TRUE"]
         (format {:select [:foo]
                  :from [:bar]
                  :join [[:table :t] true]}))))

(deftest cross-join-test
  (is (= ["SELECT * FROM foo CROSS JOIN bar"]
         (format {:select [:*]
                  :from [:foo]
                  :cross-join [:bar]})))
  (is (= ["SELECT * FROM foo AS f CROSS JOIN bar b"]
         (format {:select [:*]
                  :from [[:foo :f]]
                  :cross-join [[:bar :b]]}))))

(deftest locking-select-tests
  (testing "PostgreSQL/ANSI FOR"
    (is (= ["SELECT * FROM foo FOR UPDATE"]
           (format {:select [:*] :from :foo :for :update})))
    (is (= ["SELECT * FROM foo FOR NO KEY UPDATE"]
           (format {:select [:*] :from :foo :for :no-key-update})))
    (is (= ["SELECT * FROM foo FOR SHARE"]
           (format {:select [:*] :from :foo :for :share})))
    (is (= ["SELECT * FROM foo FOR KEY SHARE"]
           (format {:select [:*] :from :foo :for :key-share})))
    (is (= ["SELECT * FROM foo FOR UPDATE"]
           (format {:select [:*] :from :foo :for [:update]})))
    (is (= ["SELECT * FROM foo FOR NO KEY UPDATE"]
           (format {:select [:*] :from :foo :for [:no-key-update]})))
    (is (= ["SELECT * FROM foo FOR SHARE"]
           (format {:select [:*] :from :foo :for [:share]})))
    (is (= ["SELECT * FROM foo FOR KEY SHARE"]
           (format {:select [:*] :from :foo :for [:key-share]})))
    (is (= ["SELECT * FROM foo FOR UPDATE NOWAIT"]
           (format {:select [:*] :from :foo :for [:update :nowait]})))
    (is (= ["SELECT * FROM foo FOR UPDATE OF bar NOWAIT"]
           (format {:select [:*] :from :foo :for [:update :bar :nowait]})))
    (is (= ["SELECT * FROM foo FOR UPDATE WAIT"]
           (format {:select [:*] :from :foo :for [:update :wait]})))
    (is (= ["SELECT * FROM foo FOR UPDATE OF bar WAIT"]
           (format {:select [:*] :from :foo :for [:update :bar :wait]})))
    (is (= ["SELECT * FROM foo FOR UPDATE SKIP LOCKED"]
           (format {:select [:*] :from :foo :for [:update :skip-locked]})))
    (is (= ["SELECT * FROM foo FOR UPDATE OF bar SKIP LOCKED"]
           (format {:select [:*] :from :foo :for [:update :bar :skip-locked]})))
    (is (= ["SELECT * FROM foo FOR UPDATE OF bar, quux"]
           (format {:select [:*] :from :foo :for [:update [:bar :quux]]}))))
  (testing "MySQL for/lock"
    ;; these examples come from:
    (is (= ["SELECT * FROM t1 WHERE c1 = (SELECT c1 FROM t2) FOR UPDATE"] ; portable
           (format {:select [:*] :from :t1
                    :where [:= :c1 {:select [:c1] :from :t2}]
                    :for [:update]})))
    (is (= ["SELECT * FROM t1 WHERE c1 = (SELECT c1 FROM t2 FOR UPDATE) FOR UPDATE"]
           (format {:select [:*] :from :t1
                    :where [:= :c1 {:select [:c1] :from :t2 :for [:update]}]
                    :for [:update]})))
    (is (= ["SELECT * FROM foo WHERE name = 'Jones' LOCK IN SHARE MODE"] ; MySQL-specific
           (format {:select [:*] :from :foo
                    :where [:= :name [:inline "Jones"]]
                    :lock [:in-share-mode]}
                   {:dialect :mysql :quoted false})))))

(deftest insert-example-tests
  ;; these examples are taken from https://www.postgresql.org/docs/13/sql-insert.html
  (is (= ["
INSERT INTO films
VALUES ('UA502', 'Bananas', 105, '1971-07-13', 'Comedy', '82 minutes')
"]
         (format {:insert-into :films
                  :values [[[:inline "UA502"] [:inline "Bananas"] [:inline 105]
                            [:inline "1971-07-13"] [:inline "Comedy"]
                            [:inline "82 minutes"]]]}
                 {:pretty true})))
  (is (= ["
INSERT INTO films
VALUES (?, ?, ?, ?, ?, ?)
" "UA502", "Bananas", 105, "1971-07-13", "Comedy", "82 minutes"]
         (format {:insert-into :films
                  :values [["UA502" "Bananas" 105 "1971-07-13" "Comedy" "82 minutes"]]}
                 {:pretty true})))
  (is (= ["
INSERT INTO films (code, title, did, date_prod, kind)
VALUES (?, ?, ?, ?, ?)
" "T_601", "Yojimo", 106, "1961-06-16", "Drama"]
         (format {:insert-into :films
                  :columns [:code :title :did :date_prod :kind]
                  :values [["T_601", "Yojimo", 106, "1961-06-16", "Drama"]]}
                 {:pretty true})))
  (is (= ["
INSERT INTO films
VALUES (?, ?, ?, DEFAULT, ?, ?)
" "UA502", "Bananas", 105, "Comedy", "82 minutes"]
         (format {:insert-into :films
                  :values [["UA502" "Bananas" 105 [:default] "Comedy" "82 minutes"]]}
                 {:pretty true})))
  (is (= ["
INSERT INTO films (code, title, did, date_prod, kind)
VALUES (?, ?, ?, DEFAULT, ?)
" "T_601", "Yojimo", 106, "Drama"]
         (format {:insert-into :films
                  :columns [:code :title :did :date_prod :kind]
                  :values [["T_601", "Yojimo", 106, [:default], "Drama"]]}
                 {:pretty true}))))

(deftest on-conflict-tests
  ;; these examples are taken from https://www.postgresqltutorial.com/postgresql-upsert/
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT ON CONSTRAINT customers_name_key
DO NOTHING
"]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict {:on-constraint :customers_name_key}
                  :do-nothing true}
                 {:pretty true})))
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT
ON CONSTRAINT customers_name_key
DO NOTHING
"]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict []
                  :on-constraint :customers_name_key
                  :do-nothing true}
                 {:pretty true})))
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT (name)
DO NOTHING
"]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict :name
                  :do-nothing true}
                 {:pretty true})))
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT (name)
DO NOTHING
"]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict [:name]
                  :do-nothing true}
                 {:pretty true})))
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT ((foo + ?), name, (TRIM(email)))
DO NOTHING
" 1]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict [[:+ :foo 1] :name [:trim :email]]
                  :do-nothing true}
                 {:pretty true})))
  (is (= ["
INSERT INTO customers (name, email)
VALUES ('Microsoft', 'hotline@microsoft.com')
ON CONFLICT (name)
DO UPDATE SET email = EXCLUDED.email || ';' || customers.email
"]
         (format {:insert-into :customers
                  :columns [:name :email]
                  :values [[[:inline "Microsoft"], [:inline "hotline@microsoft.com"]]]
                  :on-conflict :name
                  :do-update-set {:email [:|| :EXCLUDED.email [:inline ";"] :customers.email]}}
                 {:pretty true}))))

(deftest issue-285
  (is (= ["
SELECT *
FROM processes
WHERE state = ?
ORDER BY id = ? DESC
" 42 123]
         (format (-> (h/select :*)
                     (h/from :processes)
                     (h/where [:= :state 42])
                     (h/order-by [[:= :id 123] :desc]))
                 {:pretty true}))))

(deftest issue-299-test
  (let [name    "test field"
        ;; this was a bug in 1.x -- adding here to prevent regression:
        enabled [true, "); SELECT case when (SELECT current_setting('is_superuser'))='off' then pg_sleep(0.2) end; -- "]]
    (is (= ["INSERT INTO table (name, enabled) VALUES (?, (TRUE, ?))" name (second enabled)]
           (format {:insert-into :table
                    :values [{:name name
                              :enabled enabled}]})))))

(deftest issue-425-default-values-test
  (testing "default values"
    (is (= ["INSERT INTO table (a, b, c) DEFAULT VALUES"]
           (format {:insert-into [:table [:a :b :c]] :values :default}))))
  (testing "values with default row"
    (is (= ["INSERT INTO table (a, b, c) VALUES (1, 2, 3), DEFAULT, (4, 5, 6)"]
           (format {:insert-into [:table [:a :b :c]]
                    :values [[1 2 3] :default [4 5 6]]}
                   {:inline true}))))
  (testing "values with default column"
    (is (= ["INSERT INTO table (a, b, c) VALUES (1, DEFAULT, 3), DEFAULT"]
           (format {:insert-into [:table [:a :b :c]]
                    :values [[1 [:default] 3] :default]}
                   {:inline true}))))
  (testing "map values with default row, no columns"
    (is (= ["INSERT INTO table (a, b, c) VALUES (1, 2, 3), DEFAULT, (4, 5, 6)"]
           (format {:insert-into :table
                    :values [{:a 1 :b 2 :c 3} :default {:a 4 :b 5 :c 6}]}
                   {:inline true}))))
  (testing "map values with default column, no columns"
    (is (= ["INSERT INTO table (a, b, c) VALUES (1, DEFAULT, 3), DEFAULT"]
           (format {:insert-into :table
                    :values [{:a 1 :b [:default] :c 3} :default]}
                   {:inline true}))))
  (testing "empty values"
    (is (= ["INSERT INTO table (a, b, c) VALUES ()"]
           (format {:insert-into [:table [:a :b :c]]
                    :values []})))))

(deftest issue-316-test
  ;; this is a pretty naive test -- there are other tricks to perform injection
  ;; that are not detected by HoneySQL and you should generally use :quoted true
  (testing "SQL injection via keyword is detected"
    (let [sort-column "foo; select * from users"]
      (try
        (-> {:select [:foo :bar]
             :from [:mytable]
             :order-by [(keyword sort-column)]}
            (format))
        (is false "; not detected in entity!")
        (catch #?(:cljs :default :default Exception) e
          (is (:disallowed (ex-data e))))))))
    ;; should not produce: ["SELECT foo, bar FROM mytable ORDER BY foo; select * from users"]

(deftest issue-319-test
  (testing "that registering a clause is idempotent"
    (is (= ["FOO"]
           (do
             (sut/register-clause! :foo (constantly ["FOO"]) nil)
             (sut/register-clause! :foo (constantly ["FOO"]) nil)
             (format {:foo []}))))))

(deftest issue-380-test
  (testing "that registering a clause by name works"
    (is (map? (sut/register-clause! :qualify :having :window)))))

(deftest issue-401-dialect
  (testing "registering a dialect that upper-cases idents"
    (sut/register-dialect! ::MYSQL (update (sut/get-dialect :mysql) :quote comp sut/upper-case))
    (is (= ["SELECT `foo` FROM `bar`"]
           (sut/format {:select :foo :from :bar} {:dialect :mysql})))
    (is (= ["SELECT `FOO` FROM `BAR`"]
           (sut/format {:select :foo :from :bar} {:dialect ::MYSQL})))))

(deftest issue-321-linting
  (testing "empty IN is ignored by default"
    (is (= ["WHERE x IN ()"]
           (format {:where [:in :x []]})))
    (is (= ["WHERE x IN ()"]
           (format {:where [:in :x :?y]}
                   {:params {:y []}}))))
  (testing "empty IN is flagged in basic mode"
    (is (thrown-with-msg? ExceptionInfo #"empty collection"
                          (format {:where [:in :x []]}
                                  {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"empty collection"
                          (format {:where [:in :x :?y]}
                                  {:params {:y []} :checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"empty collection"
                          (format '{where (in x [])}
                                  {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"empty collection"
                          (format '{where (in x ?y)}
                                  {:params {:y []} :checking :basic}))))
  (testing "IN NULL is ignored by default and basic"
    (is (= ["WHERE x IN (NULL)"]
           (format {:where [:in :x [nil]]})))
    (is (= ["WHERE x IN (NULL)"]
           (format {:where [:in :x [nil]]}
                   {:checking :basic})))
    (is (= ["WHERE x IN (?)" nil]
           (format {:where [:in :x :?y]}
                   {:params {:y [nil]}})))
    (is (= ["WHERE x IN (?)" nil]
           (format {:where [:in :x :?y]}
                   {:params {:y [nil]} :checking :basic})))
    (is (= ["WHERE x IN ($2)" nil nil]
           (format {:where [:in :x :?y]}
                   {:params {:y [nil]} :numbered true})))
    (is (= ["WHERE x IN ($2)" nil nil]
           (format {:where [:in :x :?y]}
                   {:params {:y [nil]} :checking :basic :numbered true}))))
  (testing "IN NULL is flagged in strict mode"
    (is (thrown-with-msg? ExceptionInfo #"does not match"
                          (format {:where [:in :x [nil]]}
                                  {:checking :strict})))
    (is (thrown-with-msg? ExceptionInfo #"does not match"
                          (format {:where [:in :x :?y]}
                                  {:params {:y [nil]} :checking :strict})))
    (is (thrown-with-msg? ExceptionInfo #"does not match"
                          (format '{where (in x [nil])}
                                  {:checking :strict})))
    (is (thrown-with-msg? ExceptionInfo #"does not match"
                          (format '{where (in x ?y)}
                                  {:params {:y [nil]} :checking :strict}))))
  (testing "empty WHERE clauses ignored with none"
    (is (= ["DELETE FROM foo"]
           (format {:delete-from :foo})))
    (is (= ["DELETE foo"]
           (format {:delete :foo})))
    (is (= ["UPDATE foo SET x = ?" 1]
           (format {:update :foo :set {:x 1}}))))
  (testing "non-empty WHERE clauses valid in basic mode"
    (is (= ["DELETE FROM foo WHERE x = ?" 1]
           (format {:delete-from :foo :where [:= :x 1]} {:checking :basic})))
    (is (= ["DELETE foo WHERE x = ?" 1]
           (format {:delete :foo :where [:= :x 1]} {:checking :basic})))
    (is (= ["UPDATE foo SET x = ? WHERE x = ?" 1 1]
           (format {:update :foo :set {:x 1} :where [:= :x 1]} {:checking :basic})))
    (is (= ["DELETE FROM foo WHERE x = ?" 1]
           (format '{delete-from foo where (= x 1)} {:checking :basic})))
    (is (= ["DELETE foo WHERE x = ?" 1]
           (format '{delete foo where (= x 1)} {:checking :basic})))
    (is (= ["UPDATE foo SET x = ? WHERE x = ?" 1 1]
           (format '{update foo set {x 1} where (= x 1)} {:checking :basic}))))
  (testing "non-empty WHERE clauses valid in strict mode"
    (is (= ["DELETE FROM foo WHERE x = ?" 1]
           (format {:delete-from :foo :where [:= :x 1]} {:checking :strict})))
    (is (= ["DELETE foo WHERE x = ?" 1]
           (format {:delete :foo :where [:= :x 1]} {:checking :strict})))
    (is (= ["UPDATE foo SET x = ? WHERE x = ?" 1 1]
           (format {:update :foo :set {:x 1} :where [:= :x 1]} {:checking :strict})))
    (is (= ["DELETE FROM foo WHERE x = ?" 1]
           (format '{delete-from foo where (= x 1)} {:checking :strict})))
    (is (= ["DELETE foo WHERE x = ?" 1]
           (format '{delete foo where (= x 1)} {:checking :strict})))
    (is (= ["UPDATE foo SET x = ? WHERE x = ?" 1 1]
           (format '{update foo set {x 1} where (= x 1)} {:checking :strict}))))
  (testing "empty WHERE clauses flagged in basic mode"
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format {:delete-from :foo} {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format {:delete :foo} {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format {:update :foo :set {:x 1}} {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format '{delete-from foo} {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format '{delete foo} {:checking :basic})))
    (is (thrown-with-msg? ExceptionInfo #"without a non-empty"
                          (format '{update foo set {x 1}} {:checking :basic})))))

(deftest quoting-:%-syntax
  (testing "quoting of expressions in functions shouldn't depend on syntax"
    (is (= ["SELECT SYSDATE()"]
           (format {:select [[[:sysdate]]]})
           (format {:select :%sysdate})))
    (is (= ["SELECT COUNT(*)"]
           (format {:select [[[:count :*]]]})
           (format {:select :%count.*})))
    (is (= ["SELECT AVERAGE(`foo-foo`)"]
           (format {:select [[[:average :foo-foo]]]} :dialect :mysql)
           (format {:select :%average.foo-foo} :dialect :mysql)))
    (is (= ["SELECT GREATER(`foo-foo`, `bar-bar`)"]
           (format {:select [[[:greater :foo-foo :bar-bar]]]} :dialect :mysql)
           (format {:select :%greater.foo-foo.bar-bar} :dialect :mysql)))
    (is (= ["SELECT MIXED_KEBAB(`yum-yum`)"]
           (format {:select :%mixed-kebab.yum-yum} :dialect :mysql)))
    (is (= ["SELECT MIXED_KEBAB(`yum_yum`)"]
           (format {:select :%mixed-kebab.yum-yum} :dialect :mysql :quoted-snake true)))
    ;; qualifier is always - -> _ converted:
    (is (= ["SELECT MIXED_KEBAB(`yum_yum`.`bar-bar`, `a_b`.`c-d`)"]
           (format {:select (keyword "%mixed-kebab.yum-yum/bar-bar.a-b/c-d")} :dialect :mysql)))
    ;; name is only - -> _ converted when snake_case requested:
    (is (= ["SELECT MIXED_KEBAB(`yum_yum`.`bar_bar`, `a_b`.`c_d`)"]
           (format {:select (keyword "%mixed-kebab.yum-yum/bar-bar.a-b/c-d")} :dialect :mysql :quoted-snake true)))
    (is (= ["SELECT RANSOM(`NoTe`)"]
           (format {:select [[[:ransom :NoTe]]]} :dialect :mysql)
           (format {:select :%ransom.NoTe} :dialect :mysql))))
  (testing "issue 352: literal function calls"
    (is (= ["SELECT sysdate()"]
           (format {:select [[[:'sysdate]]]})))
    (is (= ["SELECT count(*)"]
           (format {:select [[[:'count :*]]]})))
    (is (= ["SELECT Mixed_Kebab(yum_yum)"]
           (format {:select [[[:'Mixed-Kebab :yum-yum]]]})))
    (is (= ["SELECT `Mixed-Kebab`(`yum-yum`)"]
           (format {:select [[[:'Mixed-Kebab :yum-yum]]]} :dialect :mysql)))
    (is (= ["SELECT other_project.other_dataset.other_function(?, ?)" 1 2]
           (format {:select [[[:'other-project.other_dataset.other_function 1 2]]]})))
    (is (= ["SELECT \"other-project\".\"other_dataset\".\"other_function\"(?, ?)" 1 2]
           (format {:select [[[:'other-project.other_dataset.other_function 1 2]]]} :dialect :ansi)))))

(deftest join-without-on-using
  ;; essentially issue 326
  (testing "join does not need on or using"
    (is (= ["SELECT foo FROM bar INNER JOIN quux"]
           (format {:select :foo
                    :from :bar
                    :join [:quux]}))))
  (testing "join on select with parameters"
    (is (= ["SELECT foo FROM bar INNER JOIN (SELECT a FROM b WHERE id = ?) WHERE id = ?" 123 456]
           (format {:select :foo
                    :from :bar
                    :join [{:select :a :from :b :where [:= :id 123]}]
                    :where [:= :id 456]})))
    (is (= ["SELECT foo FROM bar INNER JOIN (SELECT a FROM b WHERE id = ?) AS x WHERE id = ?" 123 456]
           (format {:select :foo
                    :from :bar
                    :join [[{:select :a :from :b :where [:= :id 123]} :x]]
                    :where [:= :id 456]})))
    (is (= ["SELECT foo FROM bar INNER JOIN (SELECT a FROM b WHERE id = ?) AS x ON y WHERE id = ?" 123 456]
           (format {:select :foo
                    :from :bar
                    :join [[{:select :a :from :b :where [:= :id 123]} :x] :y]
                    :where [:= :id 456]})))))

(deftest fetch-offset-issue-338
  (testing "default offset (with and without limit)"
    (is (= ["SELECT foo FROM bar LIMIT ? OFFSET ?" 10 20]
           (format {:select :foo :from :bar
                    :limit 10 :offset 20})))
    (is (= ["SELECT foo FROM bar OFFSET ?" 20]
           (format {:select :foo :from :bar
                    :offset 20}))))
  (testing "default offset / fetch"
    (is (= ["SELECT foo FROM bar OFFSET ? ROWS FETCH NEXT ? ROWS ONLY" 20 10]
           (format {:select :foo :from :bar
                    :fetch 10 :offset 20})))
    (is (= ["SELECT foo FROM bar OFFSET ? ROW FETCH NEXT ? ROW ONLY" 1 1]
           (format {:select :foo :from :bar
                    :fetch 1 :offset 1})))
    (is (= ["SELECT foo FROM bar FETCH FIRST ? ROWS ONLY" 2]
           (format {:select :foo :from :bar
                    :fetch 2}))))
  (testing "SQL Server offset"
    (is (= ["SELECT [foo] FROM [bar] OFFSET ? ROWS FETCH NEXT ? ROWS ONLY" 20 10]
           (format {:select :foo :from :bar
                    :fetch 10 :offset 20}
                   {:dialect :sqlserver})))
    (is (= ["SELECT [foo] FROM [bar] OFFSET ? ROWS" 20]
           (format {:select :foo :from :bar
                    :offset 20}
                   {:dialect :sqlserver})))))

(deftest sql-kw-test
  (is (= "-" (sut/sql-kw :-)))
  (is (= "-X" (sut/sql-kw :-x)))
  (is (= "X-" (sut/sql-kw :x-)))
  (is (= "-X-" (sut/sql-kw :-x-)))
  (is (= "A B" (sut/sql-kw :a-b)))
  (is (= "A B C" (sut/sql-kw :a-b-c)))
  (is (= "A B C D" (sut/sql-kw :a-b-c-d)))
  (is (= "FETCH NEXT" (sut/sql-kw :fetch-next)))
  (is (= "WHAT IS THIS" (sut/sql-kw :what-is-this)))
  (is (= "FEE FIE FOE FUM" (sut/sql-kw :fee-fie-foe-fum)))
  (is (= "-WHAT THE-" (sut/sql-kw :-what-the-)))
  (is (= "fetch_next" (sut/sql-kw :'fetch-next)))
  (is (= "what_is_this" (sut/sql-kw :'what-is-this)))
  (is (= "fee_fie_foe_fum" (sut/sql-kw :'fee-fie-foe-fum)))
  (is (= "_what_the_" (sut/sql-kw :'-what-the-))))

(deftest issue-394-quoting
  (is (= ["SELECT \"A\"\"B\""] (sut/format {:select (keyword "A\"B")} {:quoted true})))
  (is (= ["SELECT \"A\"\"B\""] (sut/format {:select (keyword "A\"B")} {:dialect :ansi})))
  (is (= ["SELECT [A\"B]"]     (sut/format {:select (keyword "A\"B")} {:dialect :sqlserver})))
  (is (= ["SELECT [A]]B]"]     (sut/format {:select (keyword "A]B")} {:dialect :sqlserver})))
  (is (= ["SELECT `A\"B`"]     (sut/format {:select (keyword "A\"B")} {:dialect :mysql})))
  (is (= ["SELECT `A``B`"]     (sut/format {:select (keyword "A`B")} {:dialect :mysql})))
  (is (= ["SELECT \"A\"\"B\""] (sut/format {:select (keyword "A\"B")} {:dialect :oracle}))))

(deftest issue-407-temporal
  (is (= ["SELECT f.* FROM foo FOR SYSTEM_TIME ALL AS f WHERE f.id = ?" 1]
         (sut/format {:select :f.* :from [[:foo :f :for :system-time :all]] :where [:= :f.id 1]})))
  (is (= ["SELECT * FROM foo FOR SYSTEM_TIME ALL WHERE id = ?" 1]
         (sut/format {:select :* :from [[:foo :for :system-time :all]] :where [:= :id 1]}))))

(deftest issue-421-mysql-replace-into
  (is (= ["INSERT INTO table VALUES (?, ?, ?)" 1 2 3]
         (sut/format {:insert-into :table :values [[1 2 3]]})))
  (is (= ["REPLACE INTO table VALUES (?, ?, ?)" 1 2 3]
         (sut/format {:replace-into :table :values [[1 2 3]]}
                     {:dialect :mysql :quoted false}))))

(deftest issue-422-quoting
  ;; default quote if strange entity:
  (is (= ["SELECT A, \"B C\""] (sut/format {:select [:A (keyword "B C")]})))
  ;; default don't quote normal entity:
  (is (= ["SELECT A, B_C"]     (sut/format {:select [:A (keyword "B_C")]})))
  ;; quote all entities when quoting enabled:
  (is (= ["SELECT \"A\", \"B C\""] (sut/format {:select [:A (keyword "B C")]}
                                               {:quoted true})))
  ;; don't quote if quoting disabled (illegal SQL):
  (is (= ["SELECT A, B C"]     (sut/format {:select [:A (keyword "B C")]}
                                           {:quoted false}))))

(deftest issue-434-case-quoting
  (is (= ["SELECT ARRAY (SELECT \"oid\" FROM \"pg_proc\" WHERE \"proname\" LIKE 'bytea%')"]
         (sut/format {:select [[[:'ARRAY {:select :oid :from :pg_proc :where [:like :proname [:inline "bytea%"]]}]]]} :quoted true))))

(deftest issue-456-format-expr
  (is (= ["`x` + ?" 1]
         (sut/format [:+ :x 1] {:dialect :mysql}))))

(deftest issue-459-variadic-ops
  (sut/register-op! :op)
  (is (= ["SELECT a"] ; not unary!
         (sut/format {:select [[[:op :a]]]})))
  (is (= ["SELECT a OP b"]
         (sut/format {:select [[[:op :a :b]]]})))
  (is (= ["SELECT a OP b OP c"]
         (sut/format {:select [[[:op :a :b :c]]]}))))

(deftest issue-461-unary-ops
  (is (= ["SELECT TRUE"]
         (sut/format {:select [[[:and true]]]})))
  (is (= ["SELECT TRUE"]
         (sut/format {:select [[[:or true]]]})))
  (is (= ["SELECT ?" 1]
         (sut/format {:select [[[:* 1]]]})))
  (is (= ["SELECT TRUE AND a AND b"]
         (sut/format {:select [[[:and true :a :b]]]})))
  (is (= ["SELECT TRUE OR a OR b"]
         (sut/format {:select [[[:or true :a :b]]]})))
  (is (= ["SELECT ? * ? * ?" 1 2 3]
         (sut/format {:select [[[:* 1 2 3]]]})))
  ;; but these three genuinely are unary:
  (is (= ["SELECT + ?" 1]
         (sut/format {:select [[[:+ 1]]]})))
  (is (= ["SELECT - ?" 1]
         (sut/format {:select [[[:- 1]]]})))
  (is (= ["SELECT ~ ?" 1] ; bitwise negation
         (sut/format {:select [[[(keyword "~") 1]]]})))
  ;; and can still be used as variadic:
  (is (= ["SELECT ? + ?" 1 2]
         (sut/format {:select [[[:+ 1 2]]]})))
  (is (= ["SELECT ? - ?" 1 2]
         (sut/format {:select [[[:- 1 2]]]})))
  (is (= ["SELECT ? ~ ?" "a" "b"] ; regex op
         (sut/format {:select [[[(keyword "~") "a" "b"]]]}))))

(deftest issue-471-interspersed-kws
  (testing "overlay"
    (is (= ["SELECT OVERLAY(foo PLACING ? FROM ? FOR ?)"
            "bar" 3 4]
           (sut/format {:select [[[:overlay :foo :!placing "bar" :!from 3 :!for 4]]]}))))
  (testing "position"
    (is (= ["SELECT POSITION(? IN bar)" "foo"]
           (sut/format {:select [[[:position "foo" :!in :bar]]]}))))
  (testing "trim"
    (is (= ["SELECT TRIM(LEADING FROM bar)"]
           (sut/format {:select [[[:trim :!leading :!from :bar]]]})))
    (is (= ["SELECT TRIM(LEADING FROM bar)"]
           (sut/format {:select [[[:trim :!leading-from :bar]]]}))))
  (testing "extract"
    (is (= ["SELECT EXTRACT(CENTURY FROM TIMESTAMP '2000-12-16 12:21:13')"]
           (sut/format {:select [[[:extract :!century :!from
                                   :!timestamp [:inline "2000-12-16 12:21:13"]]]]}))))
  (testing "xmlelement"
    (is (= ["SELECT XMLELEMENT(NAME \"foo$bar\", XMLATTRIBUTES('xyz' AS \"a&b\"))"]
           (sut/format {:select [[[:xmlelement :!name :foo$bar
                                   [:xmlattributes [:inline "xyz"] :!as :a&b]]]]})))))

(deftest issue-474-dot-selection
  (testing "basic dot selection"
    (is (= ["SELECT a.b, c.d, a.d.x, a.d.x.y"]
           (let [t :a c :d]
             (sut/format {:select [[[:. t :b]] [[:. :c c]]
                                   [[:. t c :x]] [[:. t c :x :y]]]}))))
    (is (= ["SELECT [a].[b], [c].[d], [a].[d].[x]"]
           (let [t :a c :d]
             (sut/format {:select [[[:. t :b]] [[:. :c c]] [[:. t c :x]]]}
                         {:dialect :sqlserver})))))
  (testing "basic field selection from composite"
    (is (= ["SELECT (v).*, (w).x, (Y(z)).*"]
           (sut/format '{select (((. (nest v) *))
                                 ((. (nest w) x))
                                 ((. (nest (y z)) *)))})))
    (is (= ["SELECT (`v`).*, (`w`).`x`, (Y(`z`)).*"]
           (sut/format '{select (((. (nest v) *))
                                 ((. (nest w) x))
                                 ((. (nest (y z)) *)))}
                       {:dialect :mysql})))
    (is (= ["SELECT (v).*, (w).x, (Y(z)).*"]
           (sut/format '{select (((get-in v *))
                                 ((get-in w x))
                                 ((get-in (y z) *)))})))
    (is (= ["SELECT (`v`).*, (`w`).`x`, (Y(`z`)).*"]
           (sut/format '{select (((get-in v *))
                                 ((get-in w x))
                                 ((get-in (y z) *)))}
                       {:dialect :mysql})))))

(deftest issue-570-snowflake-dot-selection
  (testing "basic colon selection"
    (is (= ["SELECT a:b, c:d, a:d.x, a:d.x.y"]
           (let [t :a c :d]
             (sut/format {:select [[[:.:. t :b]] [[:.:. :c c]]
                                   [[:.:. t c :x]] [[:.:. t c :x :y]]]}))))
    (is (= ["SELECT [a]:[b], [c]:[d], [a]:[d].[x]"]
           (let [t :a c :d]
             (sut/format {:select [[[:.:. t :b]] [[:.:. :c c]] [[:.:. t c :x]]]}
                         {:dialect :sqlserver})))))
  (testing "basic field selection from composite"
    (is (= ["SELECT (v):*, (w):x, (Y(z)):*"]
           (sut/format '{select (((.:. (nest v) *))
                                 ((.:. (nest w) x))
                                 ((.:. (nest (y z)) *)))})))
    (is (= ["SELECT (`v`):*, (`w`):`x`, (Y(`z`)):*"]
           (sut/format '{select (((.:. (nest v) *))
                                 ((.:. (nest w) x))
                                 ((.:. (nest (y z)) *)))}
                       {:dialect :mysql}))))
  (testing "bracket selection"
    (is (= ["SELECT a['b'], c['b'], a['d'].x, a:e[0].name"]
           (sut/format {:select [[[:at :a [:inline "b"]]]
                                 [[:at :c "b"]]
                                 [[:at :a [:inline "d"] :x]]
                                 [[:.:. :a [:at :e [:inline 0]] :name]]]})))
    (is (= ["SELECT a[?].name" 0]
           (sut/format '{select (((at a (lift 0) name)))})))
    ;; sanity check, compare with get-in:
    (is (= ["SELECT (a)[?].name" 0]
           (sut/format '{select (((get-in a (lift 0) name)))})))
    (is (= ["SELECT (a)['b'], (c)['b'], (a)['d'].x, a:(e)[0].name"]
           (sut/format {:select [[[:get-in :a [:inline "b"]]]
                                 [[:get-in :c "b"]]
                                 [[:get-in :a [:inline "d"] :x]]
                                 [[:.:. :a [:get-in :e [:inline 0]] :name]]]})))))

(deftest issue-476-raw
  (testing "single argument :raw"
    (is (= ["@foo := 42"]
           (sut/format [:raw "@foo := 42"])))
    (is (= ["@foo := 42"]
           (sut/format [:raw ["@foo := 42"]])))
    (is (= ["@foo := 42"]
           (sut/format [:raw ["@foo := " 42]])))
    (is (= ["@foo := (?)" 42]
           (sut/format [:raw ["@foo := " [42]]])))
    (is (= ["@foo := MYFUNC(?)" 42]
           (sut/format [:raw ["@foo := " [:myfunc 42]]]))))
  (testing "multi-argument :raw"
    (is (= ["@foo := 42"]
           (sut/format [:raw "@foo := " 42])))
    (is (= ["@foo := (?)" 42]
           (sut/format [:raw "@foo := " [42]])))
    (is (= ["@foo := MYFUNC(?)" 42]
           (sut/format [:raw "@foo := " [:myfunc 42]])))))

(deftest issue-483-join
  (testing "single nested join"
    (is (= ["SELECT * FROM tbl1 LEFT JOIN (tbl2 INNER JOIN tbl3 USING (common_column)) ON (tbl2.col2 = tbl1.col2) AND (tbl3.col3 = tbl1.col3)"]
           (-> {:select :*
                :from :tbl1
                :left-join [[[:join :tbl2 {:join [:tbl3 [:using [:common_column]]]}]]
                            [:and
                             [:= :tbl2.col2 :tbl1.col2]
                             [:= :tbl3.col3 :tbl1.col3]]]}
               (sut/format)))))
  (testing "multiple nested join"
    (is (= ["SELECT * FROM tbl1 LEFT JOIN (tbl2 INNER JOIN tbl3 USING (common_column) RIGHT JOIN tbl4 USING (id)) ON (tbl2.col2 = tbl1.col2) AND (tbl3.col3 = tbl1.col3)"]
           (-> {:select :*
                :from :tbl1
                :left-join [[[:join :tbl2
                              {:join [:tbl3 [:using [:common_column]]]}
                              {:right-join [:tbl4 [:using :id]]}]]
                            [:and
                             [:= :tbl2.col2 :tbl1.col2]
                             [:= :tbl3.col3 :tbl1.col3]]]}
               (sut/format)))))
  (testing "special syntax example"
    (is (= ["INNER JOIN (tbl1 LEFT JOIN tbl2 USING (id))"]
           (sut/format {:join [[[:join :tbl1 {:left-join [:tbl2 [:using :id]]}]]]})))))

#?(:clj
   (deftest issue-495-formatv
     (is (= ["SELECT * FROM foo WHERE x = ?" 13]
            (let [v 13 x 42]
              (assert x) ; just to mark it as used
              (sut/formatv [v] '{select * from foo where (= x v)}))))))

(deftest issue-496-overriding
  (is (= ["INSERT INTO table (a, b) OVERRIDING SYSTEM VALUE VALUES (?, ?)" 1 2]
         (sut/format {:insert-into [{:overriding-value :system} :table]
                      :columns [:a :b]
                      :values [[1 2]]})))
  (is (= ["INSERT INTO table (a, b) OVERRIDING USER VALUE VALUES (?, ?)" 1 2]
         (sut/format {:insert-into [{:overriding-value :user} :table [:a :b]]
                      :values [[1 2]]})))
  (is (= ["INSERT INTO table (a, b) OVERRIDING SYSTEM VALUE VALUES (?, ?)" 1 2]
         (sut/format {:insert-into [{:overriding-value :system} :table]
                      :values [{:a 1 :b 2}]}))))

(deftest issue-497-alias

  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:raw "\"some-alias\""]]]})))
  ;; likely illegal SQL, but shows quoted keyword escaping the -/_ replace:
  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY some-alias ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[:'some-alias]]})))
  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:alias "some-alias"]]]})))
  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY some_alias ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:alias :some-alias]]]})))
  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:alias :'some-alias]]]})))
  (is (= ["SELECT column_name AS \"some-alias\" FROM b ORDER BY \"some-alias\" ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:alias "some-alias"]]]})))
  (is (= ["SELECT \"column-name\" AS \"some-alias\" FROM \"b\" ORDER BY ? ASC"
          "some-alias"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by ["some-alias"]}
                     {:quoted true})))
  (is (= ["SELECT `column-name` AS `some-alias` FROM `b` ORDER BY `some-alias` ASC"]
         (sut/format {:select [[:column-name "some-alias"]]
                      :from :b
                      :order-by [[[:alias "some-alias"]]]}
                     {:dialect :mysql}))))

(deftest output-clause-post-501
  (sut/register-clause! :output :select :values)
  (is (= ["INSERT INTO foo (bar) OUTPUT inserted.* VALUES (?)" 1]
         (sut/format {:insert-into :foo :output [:inserted.*] :values [{:bar 1}]})))
  (is (= ["INSERT INTO foo (bar) OUTPUT inserted.* VALUES (?)" 1]
         (sut/format {:insert-into :foo :columns [:bar] :output [:inserted.*] :values [[1]]}))))

(deftest at-time-zone-503
  (is (= ["SELECT foo AT TIME ZONE 'UTC'"]
         (sut/format {:select [[[:at-time-zone :foo "UTC"]]]})))
  (is (= ["SELECT foo AT TIME ZONE 'UTC'"]
         (sut/format {:select [[[:at-time-zone :foo :UTC]]]})))
  (is (= ["SELECT FOO(bar) AT TIME ZONE 'UTC'"]
         (sut/format {:select [[[:at-time-zone [:foo :bar] :UTC]]]}))))

(deftest issue-512
  (testing "select with metadata"
    (is (= ["SELECT DISTINCT * FROM table"]
           (sut/format {:select-distinct [:*] :from [:table]})))
    (is (= ["SELECT DISTINCT * FROM table"]
           (sut/format {:select ^{:distinct true} [:*] :from [:table]})))
    (is (= ["SELECT DISTINCT * FROM table"]
           (sut/format {:select ^:distinct [:*] :from [:table]})))))

(deftest issue-515
  (testing ":always-quoting option"
    (is (= ["SELECT foo FROM table"]
           (sut/format '{select foo from table})))
    (is (= ["SELECT \"foo\" FROM \"table\""]
           (sut/format '{select foo from table}
                       {:quoted-always #"^(foo|table)$"})))
    (is (= ["SELECT \"foo\" FROM \"table\""]
           (sut/format '{select foo from table}
                       {:quoted-always #"^(foo|table)$"
                        :quoted false})))
    (is (= ["SELECT \"foo\" FROM table"]
           (sut/format '{select foo from table}
                       {:quoted-always #"^(foo)$"
                        :quoted false})))))

(deftest issue-520
  (testing ":inline with a single argument"
    (is (= ["SELECT 42 AS x"]
           (sut/format '{select [[[inline 42] x]]}))))
  (testing ":inline with multiple arguments"
    (is (= ["SELECT DATE '2024-01-06' AS x"]
           (sut/format '{select [[[inline DATE "2024-01-06"] x]]}))))
  (testing ":inline with a parameter"
    (is (= ["SELECT 42 AS x"]
           (sut/format '{select [[[inline [param foo]] x]]}
                       {:params {'foo 42}}))))
  (testing ":inline with a sequence"
    (is (= ["SELECT ('a', 'b', 'c') AS x"]
           (sut/format '{select [[[inline ["a" "b" "c"]] x]]}))))
  (testing ":inline with a lifted sequence"
    (is (= ["SELECT ['a', 'b', 'c'] AS x"]
           (sut/format '{select [[[inline [lift ["a" "b" "c"]]] x]]})))))

(deftest issue-522
  (testing "from with metadata"
    (is (= ["SELECT * FROM table WITH (HINT)"]
           (sut/format {:select [:*] :from [^:hint [:table]]})))
    ;; hash map (metadata) is unordered:
    (is (or (= ["SELECT * FROM table WITH (ABC, DEF)"]
               (sut/format {:select [:*] :from [^:abc ^:def [:table]]}))
            (= ["SELECT * FROM table WITH (DEF, ABC)"]
               (sut/format {:select [:*] :from [^:abc ^:def [:table]]}))))
    (is (or (= ["SELECT * FROM table WITH (ABC, DEF)"]
               (sut/format {:select [:*] :from [^{:abc true :def true} [:table]]}))
            (= ["SELECT * FROM table WITH (DEF, ABC)"]
               (sut/format {:select [:*] :from [^{:abc true :def true} [:table]]}))))))

(deftest issue-527-composite
  (is (= ["SELECT (a, b) AS c FROM table"]
         (sut/format {:select [[[:composite :a :b] :c]] :from [:table]})))
  (is (= ["SELECT a FROM table WHERE (b, c) = (?, ?)" 1 2]
         (sut/format {:select :a :from :table :where [:= [:composite :b :c] [:composite 1 2]]})))
  (is (= ["SELECT a, b, c FROM (VALUES (?, ?, ?), (?, ?, ?)) AS t (a, b, c)" 1 2 3 4 5 6]
         (sut/format {:select [:a :b :c]
                      :from [[{:values [[1 2 3] [4 5 6]]}
                              [:t [:composite :a :b :c]]]]}))))

(deftest issue-543-param
  (testing "quoted param with symbol param"
    (is (= ["SELECT a FROM table WHERE x = ?" 42]
           (sut/format '{select a from table where (= x (param y))}
                       {:params {'y 42}})))
    (is (= ["SELECT a FROM table WHERE x = ?" 42]
           (sut/format '{select a from table where (= x ?y)}
                       {:params {'y 42}}))))
  (testing "quoted param with keyword param"
    (is (= ["SELECT a FROM table WHERE x = ?" 42]
           (sut/format '{select a from table where (= x (param y))}
                       {:params {:y 42}})))
    (is (= ["SELECT a FROM table WHERE x = ?" 42]
           (sut/format '{select a from table where (= x :?y)}
                       {:params {:y 42}}))))
  (testing "all combinations"
    (doseq [p1 [:y 'y] p2 [:y 'y]]
      (is (= ["SELECT a FROM table WHERE x = ?" 42]
             (sut/format {:select :a :from :table :where [:= :x [:param p1]]}
                         {:params {p2 42}}))))))

(deftest issue-n-using
  (testing "all keywords"
    (is (= ["SELECT * FROM `t1` INNER JOIN `t2` USING (`id`) WHERE `t1`.`id` = ?" 1]
           (sut/format {:select :* :from :t1 :join [:t2 [:using :id]] :where [:= :t1/id 1]} {:dialect :mysql}))))
  (testing "all symbols"
    (is (= ["SELECT * FROM `t1` INNER JOIN `t2` USING (`id`) WHERE `t1`.`id` = ?" 1]
           (sut/format '{select * from t1 join (t2 (using id)) where (= t1/id 1)} {:dialect :mysql}))))
  (testing "mixed keywords and symbols"
    (is (= ["SELECT * FROM `t1` INNER JOIN `t2` USING (`id`) WHERE `t1`.`id` = ?" 1]
           (sut/format '{select * from t1 join (t2 (:using id)) where (= t1/id 1)} {:dialect :mysql})))))

(deftest issue-548-format-var-encoding
  (is (= ["CREATE TABLE \"With%20Space\""]
         (sut/format {:create-table "With%20Space"})))
  (is (= ["CREATE TABLE \"%20WithLeadingSpace\""]
         (sut/format {:create-table "%20WithLeadingSpace"})))
  #?(:clj (let [table (URLEncoder/encode "привіт")]
            (is (= [(str "CREATE TABLE \"" table "\"")]
                   (sut/format {:create-table table}))))))

(deftest issue-555-setting
  (testing "setting default time"
    (is (= ["SETTING DEFAULT SYSTEM_TIME AS OF DATE '2024-11-24'"]
           (sut/format {:setting [:default :system-time :as-of [:inline :DATE "2024-11-24"]]})))
    (is (= ["SETTING SNAPSHOT_TIME TO DATE '2024-11-24', DEFAULT VALID_TIME TO BETWEEN DATE '2022' AND DATE '2023'"]
           (sut/format {:setting [[:snapshot-time :to [:inline :DATE "2024-11-24"]]
                                  [:default :valid-time :to :between [:inline :DATE "2022"] :and [:inline :DATE "2023"]]]})))
    (is (= ["SETTING DEFAULT SYSTEM_TIME AS OF DATE '2024-11-24' SELECT * FROM table"]
           (sut/format (-> (h/setting :default :system-time :as-of [:inline :DATE "2024-11-24"])
                           (h/select :*)
                           (h/from :table)))))
    (is (= ["SETTING SNAPSHOT_TIME TO DATE '2024-11-24', DEFAULT VALID_TIME TO BETWEEN DATE '2022' AND DATE '2023' SELECT * FROM table"]
           (sut/format (-> (h/setting [:snapshot-time :to [:inline :DATE "2024-11-24"]]
                                      [:default :valid-time :to :between [:inline :DATE "2022"] :and [:inline :DATE "2023"]])
                           (h/select :*)
                           (h/from :table)))))))

(deftest issue-571
  (testing "an empty where clause is omitted"
    (is (= ["SELECT * FROM foo"]
           (sut/format {:select :* :from :foo :where []})))
    #?(:clj
       (is (thrown? clojure.lang.ExceptionInfo
                    (sut/format {:select :* :from :foo :where nil}))))
    (is (= ["SELECT * FROM foo WHERE 1 = 1"]
           (sut/format {:select :* :from :foo :where [:= 1 1]} {:inline true}))))
  (testing "an empty order by clause is omitted"
    (is (= ["SELECT * FROM foo"]
           (sut/format {:select :* :from :foo :order-by []})))
    #?(:clj
       (is (thrown? clojure.lang.ExceptionInfo
                    (sut/format {:select :* :from :foo :order-by nil}))))
    (is (= ["SELECT * FROM foo ORDER BY bar ASC"]
           (sut/format {:select :* :from :foo :order-by [:bar]})))))

(comment
  ;; partial (incorrect!) workaround for #407:
  (sut/format {:select :f.* :from [[:foo [:f :for :system-time]]] :where [:= :f.id 1]})
  ;; correct version:
  (sut/format {:select :f.* :from [[:foo :f :for :system-time]] :where [:= :f.id 1]})
  (sut/format {:where [:= :x [:inline :DATE "2019-01-01"]]})
  ;; https://github.com/seancorfield/honeysql/issues/526
  (->
   {:create-table-as [:a-b.b-c.c-d]
    :select          [:*]
    :from            [:a-b.b-c.c-d]}
   (sut/format {:dialect :nrql}))
  (sut/format {:select :a:b.c}) ; quotes a:b
  (sut/format [:. :a :b :c]) ; a.b.c
  (sut/format [:. :a :b :c :d]) ; drops d  ; a.b.c
  (sut/format [:.:. :a :b :c]) ; .(a, b, c)
  (sut/format '(.:. a b c)) ; .(a, b, c)
  )
