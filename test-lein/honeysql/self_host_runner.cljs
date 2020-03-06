(ns honeysql.self-host-runner
  (:require [cljs.test :as t :refer-macros [run-tests]]
            honeysql.core-test
            honeysql.format-test))

(enable-console-print!)

(run-tests 'honeysql.core-test
           'honeysql.format-test)
