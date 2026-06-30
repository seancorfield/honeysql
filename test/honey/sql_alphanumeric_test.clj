(ns honey.sql-alphanumeric-test
  (:require [clojure.string :as str]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.generators :as gen']
            [honey.sql :as sut]))

(def alphanumeric @#'sut/alphanumeric)

(defn alphanumeric?
  [s]
  (boolean (re-find alphanumeric s)))

(defspec matches-token-spec-for-matching 1000
  (prop/for-all [s (gen'/string-from-regex
                    (let [s (str alphanumeric)] ; remove anchors
                      (re-pattern (subs s 1 (dec (count s))))))]
                (is
                 (=
                  (sut/alphanumeric? s)
                  (alphanumeric? s)))))
(def pattern
  (re-pattern
   (str
    "["
    "\r"
    "\n"
    (char 133) ; ever heard about NE(xt)L(ine)?
    "]+$")))

(defn strip-trailing-newlines
  [s]
  (str/replace s pattern ""))

(defspec matches-token-spec-for-random-string 1000
  (prop/for-all [s (gen/fmap strip-trailing-newlines gen/string)]
                (is
                 (=
                  (sut/alphanumeric? s)
                  (alphanumeric? s)))))

(comment
  (def s "__abcdefghijklmnop")
  (dotimes [_ 10]
    (time
     (dotimes [_ 1e6]
       #_(sut/alphanumeric? s)
       (alphanumeric? s)))))
