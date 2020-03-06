(ns honeysql.test
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [cljs.test    :as t :refer-macros [is are deftest testing]]
   honeysql.core-test
   honeysql.format-test))

(doo-tests 'honeysql.core-test
           'honeysql.format-test)
