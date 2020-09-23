;; copyright (c) 2020 sean corfield, all rights reserved

(ns honey.sql
  "Primary API for HoneySQL 2.x."
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]))

;; default formatting for known clauses

(declare format-dsl)
(declare format-expr)

;; dynamic dialect handling for formatting

(def ^:private default-clause-order
  "The (default) order for known clauses. Can have items added and removed."
  [:intersect :union :union-all :except
   :select :insert-into :update :delete :delete-from :truncate
   :columns :set :from
   :join :left-join :right-join :inner-join :outer-join :full-join
   :cross-join
   :where :group-by :having :order-by :limit :offset :values])

(defn- add-clause-before
  "Low-level helper just to insert a new clause."
  [order clause before]
  (if before
    (do
      (when-not (contains? (set order) before)
        (throw (ex-info (str "Unrecognized clause: " before)
                        {:known-clauses order})))
      (reduce (fn [v k]
                (if (= k before)
                  (conj v clause k)
                  (conj v k)))
              []
              order))
    (conj order clause)))

(def ^:private dialects
  {:ansi  {:quote #(str \" % \")}
   :mssql {:quote #(str \[ % \])}
   :mysql {:quote #(str \` % \`)
           :clause-order-fn #(add-clause-before
                              (filterv (complement #{:set}) %)
                              :set
                              :where)}})

; should become defonce
(def ^:private default-dialect (atom (:ansi dialects)))

(def ^:private ^:dynamic *dialect* nil)
(def ^:private ^:dynamic *clause-order* nil)
(def ^:private ^:dynamic *quoted* nil)

;; clause helpers

;; String.toUpperCase() or `str/upper-case` for that matter converts the
;; string to uppercase for the DEFAULT LOCALE. Normally this does what you'd
;; expect but things like `inner join` get converted to `İNNER JOİN` (dot over
;; the I) when user locale is Turkish. This predictably has bad consequences
;; for people who like their SQL queries to work. The fix here is to use
;; String.toUpperCase(Locale/US) instead which always converts things the
;; way we'd expect.
;;
;; Use this instead of `str/upper-case` as it will always use Locale/US.
(def ^:private ^{:arglists '([s])} upper-case
  ;; TODO - not sure if there's a JavaScript equivalent here we should be using as well
  #?(:clj (fn [^String s] (.. s toString (toUpperCase (java.util.Locale/US))))
     :cljs str/upper-case))

(defn- sql-kw [k]
  (-> k (name) (upper-case) (str/replace "-" " ")))

(defn- format-entity [x]
  (let [q (if *quoted* (:quote *dialect*) identity)
        [t c] (if-let [n (namespace x)]
                [n (name x)]
                (let [[t c] (str/split (name x) #"\.")]
                  (if c [t c] [nil t])))]
    (cond->> c
      (not= "*" c)
      (q)
      t
      (str (q t) "."))))

(defn- format-selectable [x]
  (cond (sequential? x)
        (str (let [s (first x)]
               (if (map? s)
                 (format-dsl s true)
                 (format-entity s)))
             #_" AS " " "
             (format-entity (second x)))

        :else
        (format-entity x)))

(defn- format-selectable-dsl [x]
  (cond (map? x)
        (format-dsl x true)

        (sequential? x)
        (let [s (first x)
              [sql & params] (if (map? s) (format-dsl s true) [(format-entity s)])]
          (into [(str sql #_" AS " " " (format-entity (second x)))] params))

        (keyword? x)
        [(format-entity x)]

        :else
        (format-expr x)))

;; primary clauses

(defn- format-on-set-op [k xs]
  (let [[sqls params]
        (reduce (fn [[sql params] [sql' & params']]
                  [(conj sql sql') (if params' (into params params') params)])
                [[] []]
                (map #'format-dsl xs))]
    (into [(str/join (str " " (sql-kw k) " ") sqls)] params)))

(defn- format-selector [k xs]
  (if (sequential? xs)
    (let [[sqls params]
          (reduce (fn [[sql params] [sql' & params']]
                    [(conj sql sql') (if params' (into params params') params)])
                  [[] []]
                  (map #'format-selectable-dsl xs))]
      (into [(str (sql-kw k) " " (str/join ", " sqls))] params))
    (let [[sql & params] (format-selectable-dsl xs)]
      (into [(str (sql-kw k) " " sql)] params))))

(defn- format-insert [k table]
  ;; table can be just a table, a pair of table and statement, or a
  ;; pair of a pair of table and columns and a statement (yikes!)
  (if (sequential? table)
    (if (sequential? (first table))
      (let [[[table cols] statement] table
            [sql & params] (format-dsl statement)]
        (into [(str (sql-kw k) " " (format-selectable table)
                    " ("
                    (str/join ", " (map #'format-selectable cols))
                    ") "
                    sql)]
              params))
      (let [[table statement] table
            [sql & params] (format-dsl statement)]
        (into [(str (sql-kw k) " " (format-selectable table)
                    " " sql)]
              params)))
    [(str (sql-kw k) " " (format-selectable table))]))

(defn- format-join [k [j e]]
  (let [[sql & params] (format-expr e)]
    ;; for backward compatibility, treat plain JOIN as INNER JOIN:
    (into [(str (sql-kw (if (= :join k) :inner-join k)) " "
                (format-selectable j) " ON "
                sql)]
          params)))

(defn- format-on-expr [k e]
  (let [[sql & params] (format-expr e)]
    (into [(str (sql-kw k) " " sql)] params)))

(defn- format-expr-list [xs]
  (reduce (fn [[sql params] [sql' & params']]
            [(conj sql sql') (if params' (into params params') params)])
          [[] []]
          (map #'format-expr xs)))

(defn- format-group-by [k xs]
  (let [[sqls params] (format-expr-list xs)]
    (into [(str (sql-kw k) " " (str/join ", " sqls))] params)))

(defn- format-order-by [k xs]
  (let [dirs (map #(if (sequential? %) (second %) :asc) xs)
        [sqls params]
        (format-expr-list (map #(if (sequential? %) (first %) %) xs))]
    (into [(str (sql-kw k) " "
                (str/join ", " (map (fn [sql dir] (str sql " " (sql-kw dir)))
                                    sqls
                                    dirs)))] params)))

(defn- format-values [k xs]
  (if (sequential? (first xs))
    ;; [[1 2 3] [4 5 6]]
    (let [[sqls params]
          (reduce (fn [[sql params] [sqls' params']]
                    [(conj sql (str "(" (str/join ", " sqls') ")"))
                     (into params params')])
                  [[] []]
                  (map #'format-expr-list xs))]
      (into [(str (sql-kw k) " " (str/join ", " sqls))] params))
    ;; [1 2 3]
    (let [[sqls params] (format-expr-list xs)]
      (into [(str (sql-kw k) " (" (str/join ", " sqls) ")")] params))))

(defn- format-set-exprs [k xs]
  ;; TODO: !!!
  ["SET a = ?, b = ?" 42 13])

(def ^:private current-clause-order
  "The (current) order for known clauses. Can have items added and removed."
  (atom default-clause-order))

(def ^:private clause-format
  "The (default) behavior for each known clause. Can also have items added
  and removed."
  (atom {:intersect      #'format-on-set-op
         :union          #'format-on-set-op
         :union-all      #'format-on-set-op
         :except         #'format-on-set-op
         :select         #'format-selector
         :insert-into    #'format-insert
         :update         #'format-selector
         :delete         #'format-selector
         :delete-from    #'format-selector
         :truncate       #'format-selector
         :columns        #'format-selector
         :set            #'format-set-exprs
         :from           #'format-selector
         :join           #'format-join
         :left-join      #'format-join
         :right-join     #'format-join
         :inner-join     #'format-join
         :outer-join     #'format-join
         :full-join      #'format-join
         :cross-join     #'format-selector
         :where          #'format-on-expr
         :group-by       #'format-group-by
         :having         #'format-on-expr
         :order-by       #'format-order-by
         :limit          #'format-on-expr
         :offset         #'format-on-expr
         :values         #'format-values}))

(assert (= (set @current-clause-order) (set (keys @clause-format))))

(comment :target
  {:with 20
   :with-recursive 30
   ;:intersect 35
   ;:union 40
   ;:union-all 45
   ;:except 47
   ;:select 50
   ;:insert-into 60
   ;:update 70
   ;:delete 75
   ;:delete-from 80
   ;:truncate 85
   ;:columns 90
   :composite 95
   :set0 100 ; low-priority set clause
   ;:from 110
   ;:join 120
   ;:left-join 130
   ;:right-join 140
   ;:full-join 150
   ;:cross-join 152 ; doesn't have on clauses
   :set 155
   :set1 156 ; high-priority set clause (synonym for :set)
   ;:where 160
   ;:group-by 170
   ;:having 180
   ;:order-by 190
   ;:limit 200
   ;:offset 210
   :lock 215
   :values 220
   :query-values 230})

(defn- format-dsl [x & [nested?]]
  (let [[sqls params leftover]
        (reduce (fn [[sql params leftover] k]
                  (if-let [xs (k x)]
                    (let [formatter (k @clause-format)
                          [sql' & params'] (formatter k xs)]
                      [(conj sql sql')
                       (if params' (into params params') params)
                       (dissoc leftover k)])
                    [sql params leftover]))
                [[] [] x]
                *clause-order*)]
    (if (seq leftover)
      (do
        ;; TODO: for testing purposes, make this less noisy
        (println (str "\n-------------------\nUnknown SQL clauses: "
                      (str/join ", " (keys leftover))))
        #_(throw (ex-info (str "Unknown SQL clauses: "
                               (str/join ", " (keys leftover)))
                          leftover))
        [(str "<unknown" (str/join (keys leftover)) ">")])
      (into [(cond-> (str/join " " sqls)
               nested? (as-> s (str "(" s ")")))] params))))

(def ^:private infix-aliases
  "Provided for backward compatibility with earlier HoneySQL versions."
  {:is :=
   :is-not :<>
   :not= :<>
   :!= :<>
   :regex :regexp})

(def ^:private infix-ops
  (-> #{"mod" "and" "or" "xor" "<>" "<=" ">="
        "in" "not-in" "like" "not-like" "regexp"
        "is" "is-not" "not=" "!=" "regex"}
      (into (map str "+-*/%|&^=<>"))
      (into (keys infix-aliases))
      (into (vals infix-aliases))
      (->> (into #{} (map keyword)))))

(def ^:private special-syntax
  {:between
   (fn [[x a b]]
     (let [[sql-x & params-x] (format-expr x true)
           [sql-a & params-a] (format-expr a true)
           [sql-b & params-b] (format-expr b true)]
       (-> [(str sql-x " BETWEEN " sql-a " AND " sql-b)]
           (into params-x)
           (into params-a)
           (into params-b))))
   :cast
   (fn [[x type]]
     (let [[sql & params] (format-expr x)]
       (into [(str "CAST(" sql " AS " (sql-kw type) ")")] params)))
   :interval
   (fn [[n units]]
     (let [[sql & params] (format-expr n)]
       (into [(str "INTERVAL " sql " " (sql-kw units))] params)))})

(defn format-expr [x & [nested?]]
  (cond (keyword? x)
        [(format-entity x)]

        (map? x)
        (format-dsl x true)

        (sequential? x)
        (let [op (first x)]
          (if (keyword? op)
            (cond (infix-ops op)
                  (let [[_ a b] x
                        [s1 & p1] (format-expr a true)
                        [s2 & p2] (format-expr b true)]
                    (-> (str s1 " "
                             (sql-kw (get infix-aliases op op))
                             " " s2)
                        (cond-> nested?
                          (as-> s (str "(" s ")")))
                        (vector)
                        (into p1)
                        (into p2)))
                  (special-syntax op)
                  (let [formatter (special-syntax op)]
                    (formatter (rest x)))
                  :else
                  (let [[sqls params] (format-expr-list (rest x))]
                    (into [(str (sql-kw op)
                                "(" (str/join ", " sqls) ")")]
                          params)))
            (into [(str "(" (str/join ","
                                      (repeat (count x) "?")) ")")]
                  x)))

        :else
        ["?" x]))

(defn- check-dialect [dialect]
  (when-not (contains? dialects dialect)
    (throw (ex-info (str "Invalid dialect: " dialect)
                    {:valid-dialects (vec (sort (keys dialects)))})))
  dialect)

(defn format
  "Turn the data DSL into a vector containing a SQL string followed by
  any parameter values that were encountered in the DSL structure."
  ([data] (format data {}))
  ([data opts]
   (let [dialect? (contains? opts :dialect)
         dialect  (when dialect? (get dialects (check-dialect (:dialect opts))))]
     (binding [*dialect* (if dialect? dialect @default-dialect)
               *clause-order* (if dialect?
                                (if-let [f (:clause-order-fn dialect)]
                                  (f @current-clause-order)
                                  @current-clause-order)
                                @current-clause-order)
               *quoted*  (if (contains? opts :quoted)
                           (:quoted opts)
                           dialect?)]
       (format-dsl data)))))

(defn set-dialect!
  "Set the default dialect for formatting.

  Can be: `:ansi` (the default), `:mssql`, `:mysql`."
  [dialect]
  (reset! default-dialect (get dialects (check-dialect dialect)))
  (when-let [f (:clause-order-fn @default-dialect)]
    (swap! current-clause-order f)))

(defn register-clause!
  "Register a new clause formatter. If `before` is `nil`, the clause is
  added to the end of the list of known clauses, otherwise it is inserted
  immediately prior to that clause."
  [clause formatter before]
  (swap! current-clause-order add-clause-before clause before)
  (swap! clause-format assoc clause formatter))

(comment
  (format {:truncate :foo})
  (format-expr [:= :id 1])
  (format-expr [:+ :id 1])
  (format-expr [:+ 1 [:+ 1 :quux]])
  (format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])
  (format-expr :id)
  (format-expr 1)
  (format-on-expr :where [:= :id 1])
  (format-dsl {:select [:*] :from [:table] :where [:= :id 1]})
  (format {:select [:t.*] :from [[:table :t]] :where [:= :id 1]} {})
  (format {:select [:*] :from [:table] :group-by [:foo :bar]} {})
  (format {:select [:*] :from [:table] :group-by [[:date :bar]]} {})
  (format {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]} {})
  (format {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]} {})
  (format {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]} {})
  (format-expr [:interval 30 :days])
  (format {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})
  (format {:select [:*] :from [:table] :where [:in :id [1 2 3 4]]} {})
  ,)
