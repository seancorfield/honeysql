;; copyright (c) 2020 sean corfield, all rights reserved

(ns honey.specs
  "Optional namespace containing `clojure.spec` representations of
  the data format used as the underlying DSL for HoneySQL."
  (:require [clojure.spec.alpha :as s]))

(s/def ::sql-expression any?)

(s/def ::dsl (s/map-of simple-keyword?
                       (s/coll-of ::sql-expression
                                  :kind vector?
                                  :min-count 1)))
