(ns honey.sql.util
  "Utility functions for the main honey.sql namespace."
  (:refer-clojure :exclude [str])
  (:require clojure.string))

#?(:clj (set! *warn-on-reflection* true))

(defn str
  "More efficient implementation of `clojure.core/str` because it has more
  non-variadic arities. Optimization is Clojure-only, on other platforms it
  reverts back to `clojure.core/str`."
  (^String [] "")
  (^String [^Object a]
   #?(:clj (if (nil? a) "" (.toString a))
      :default (clojure.core/str a)))
  (^String [^Object a, ^Object b]
   #?(:clj (if (nil? a)
             (str b)
             (if (nil? b)
               (.toString a)
               (.concat (.toString a) (.toString b))))
      :default (clojure.core/str a b)))
  (^String [a b c]
   #?(:clj (let [sb (StringBuilder.)]
             (.append sb (str a))
             (.append sb (str b))
             (.append sb (str c))
             (.toString sb))
      :default (clojure.core/str a b c)))
  (^String [a b c d]
   #?(:clj (let [sb (StringBuilder.)]
             (.append sb (str a))
             (.append sb (str b))
             (.append sb (str c))
             (.append sb (str d))
             (.toString sb))
      :default (clojure.core/str a b c d)))
  (^String [a b c d e]
   #?(:clj (let [sb (StringBuilder.)]
             (.append sb (str a))
             (.append sb (str b))
             (.append sb (str c))
             (.append sb (str d))
             (.append sb (str e))
             (.toString sb))
      :default (clojure.core/str a b c d e)))
  (^String [a b c d e & more]
   #?(:clj (let [sb (StringBuilder.)]
             (.append sb (str a))
             (.append sb (str b))
             (.append sb (str c))
             (.append sb (str d))
             (.append sb (str e))
             (run! #(.append sb (str %)) more)
             (.toString sb))
      :default (apply clojure.core/str a b c d e more))))

(defn join
  "More efficient implementation of `clojure.string/join`. May accept a transducer
  `xform` to perform operations on each element before combining them together
  into a string. Clojure-only, delegates to `clojure.string/join` on other
  platforms."
  ([separator coll] (join separator identity coll))
  ([separator xform coll]
   #?(:clj
      (let [sb (StringBuilder.)
            sep (str separator)]
        (transduce xform
                   (fn
                     ([] false)
                     ([_] (.toString sb))
                     ([add-sep? x]
                      (when add-sep? (.append sb sep))
                      (.append sb (str x))
                      true))
                   false coll))

      :default
      (clojure.string/join separator (transduce xform conj [] coll)))))

(defn split-by-separator
  "More efficient implementation of `clojure.string/split` for cases when a
  literal string (not regex) is used as a separator, and for cases where the
  separator is not present in the haystack at all."
  [s sep]
  (loop [start 0, res []]
    (if-some [sep-idx (clojure.string/index-of s sep start)]
      (let [sep-idx (long sep-idx)]
        (recur (inc sep-idx) (conj res (subs s start sep-idx))))
      (if (= start 0)
        ;; Fastpath - zero separators in s
        [s]
        (conj res (subs s start))))))

(defn into*
  "An extension of `clojure.core/into` that accepts multiple \"from\" arguments.
  Doesn't support `xform`."
  ([to from1] (into* to from1 nil nil nil))
  ([to from1 from2] (into* to from1 from2 nil nil))
  ([to from1 from2 from3] (into* to from1 from2 from3 nil))
  ([to from1 from2 from3 from4]
   (if (or from1 from2 from3 from4)
     (as-> (transient to) to'
       (reduce conj! to' from1)
       (reduce conj! to' from2)
       (reduce conj! to' from3)
       (reduce conj! to' from4)
       (persistent! to'))
     to)))
