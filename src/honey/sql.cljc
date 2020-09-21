;; copyright (c) 2020 sean corfield, all rights reserved

(ns honey.sql
  "Primary API for HoneySQL 2.x."
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]))

;; default formatting for known clauses

(declare format-dsl)
(declare format-expr)

;; dynamic dialect handling for formatting

(def ^:private dialects
  {:ansi  {:quote #(str \" % \")}
   :mssql {:quote #(str \[ % \])}
   :mysql {:quote #(str \` % \`)}})

; should become defonce
(def ^:private default-dialect (atom (:ansi dialects)))

(def ^:private ^:dynamic *dialect* nil)
(def ^:private ^:dynamic *quoted* nil)

;; clause helpers

(defn- sql-kw [k]
  (-> k (name) (str/upper-case) (str/replace "-" " ")))

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
  (if (vector? x)
    (str (let [s (first x)]
           (if (map? s)
             (format-dsl s)
             (format-entity s)))
         " AS "
         (format-entity (second x)))
    (format-entity x)))

;; primary clauses

(defn- format-selector [k xs]
  [(str (sql-kw k) " " (str/join ", " (map #'format-selectable xs)))])

(defn- format-join [k [j e]]
  (let [[sql & params] (format-expr e)]
    (into [(str (sql-kw k) " " (format-selectable j) " ON " sql)] params)))

(defn- format-where [k e]
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
  (let [dirs (map #(if (vector? %) (second %) :asc) xs)
        [sqls params] (format-expr-list (map #(if (vector? %) (first %) %) xs))]
    (into [(str (sql-kw k) " "
                (str/join ", " (map (fn [sql dir] (str sql " " (sql-kw dir)))
                                    sqls
                                    dirs)))] params)))

(def ^:private clause-order
  "The (default) order for known clauses. Can have items added and removed."
  (atom [:select :from :join :where :group-by :order-by]))

(def ^:private clause-format
  "The (default) behavior for each known clause. Can also have items added
  and removed."
  (atom {:select   #'format-selector
         :from     #'format-selector
         :join     #'format-join ; any join works
         :where    #'format-where
         :group-by #'format-group-by
         :order-by #'format-order-by}))

(defn- format-dsl [x]
  (let [[sqls params]
        (reduce (fn [[sql params] k]
                  (if-let [xs (k x)]
                    (let [formatter (k @clause-format)
                          [sql' & params'] (formatter k xs)]
                      [(conj sql sql') (if params' (into params params') params)])
                    [sql params]))
                [[] []]
                @clause-order)]
    (into [(str/join " " sqls)] params)))

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
  {:cast
   (fn [[x type]]
     (let [[sql & params] (format-expr x)]
       (into [(str "CAST(" sql " AS " (sql-kw type) ")")] params)))
   :interval
   (fn [[n units]]
     (let [[sql & params] (format-expr n)]
       (into [(str "INTERVAL " sql " " (sql-kw units))] params)))})

(defn format-expr [x & nested?]
  (cond (keyword? x)
        [(format-entity x)]

        (vector? x)
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

(defn Hformat
  "Turn the data DSL into a vector containing a SQL string followed by
  any parameter values that were encountered in the DSL structure."
  ([data] (Hformat data {}))
  ([data opts]
   (let [dialect (get dialects (get opts :dialect :ansi))]
     (binding [*dialect* dialect
               *quoted*  (if (contains? opts :quoted) (:quoted opts) true)]
       (format-dsl data)))))

(defn set-dialect!
  "Set the default dialect for formatting.

  Can be: `:ansi` (the default), `:mssql`, `:mysql`."
  [dialect]
  (reset! default-dialect (get dialects dialect :ansi)))

(comment
  (format-expr [:= :id 1])
  (format-expr [:+ :id 1])
  (format-expr [:+ 1 [:+ 1 :quux]])
  (format-expr [:foo [:bar [:+ 2 [:g :abc]]] [:f 1 :quux]])
  (format-expr :id)
  (format-expr 1)
  (format-where :where [:= :id 1])
  (format-dsl {:select [:*] :from [:table] :where [:= :id 1]})
  (Hformat {:select [:t.*] :from [[:table :t]] :where [:= :id 1]})
  (Hformat {:select [:*] :from [:table] :group-by [:foo :bar]})
  (Hformat {:select [:*] :from [:table] :group-by [[:date :bar]]})
  (Hformat {:select [:*] :from [:table] :order-by [[:foo :desc] :bar]})
  (Hformat {:select [:*] :from [:table] :order-by [[[:date :expiry] :desc] :bar]})
  (Hformat {:select [:*] :from [:table] :where [:< [:date_add :expiry [:interval 30 :days]] [:now]]})
  (format-expr [:interval 30 :days])
  (Hformat {:select [:*] :from [:table] :where [:= :id 1]} {:dialect :mysql})
  ,)
