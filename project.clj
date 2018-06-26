(defproject honeysql "0.9.3-SNAPSHOT"
  :description "SQL as Clojure data structures"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/jkk/honeysql"
  :scm {:name "git"
        :url "https://github.com/jkk/honeysql"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [net.cgrand/macrovich "0.2.1"]]
  :aliases {"test-readme" ["with-profile" "midje" "midje"]}
  :cljsbuild {:builds {:release {:source-paths ["src"]
                                 :compiler {:output-to "dist/honeysql.js"
                                            :optimizations :advanced
                                            :output-wrapper false
                                            :parallel-build true
                                            :pretty-print false}}
                       :test {:source-paths ["src" "test"]
                              :compiler {:output-to "target/test/honeysql.js"
                                         :output-dir "target/test"
                                         :source-map true
                                         :main honeysql.test
                                         :parallel-build true
                                         :target :nodejs}}}}
  :doo {:build "test"}
  :tach {:test-runner-ns 'honeysql.self-host-runner
         :source-paths ["src" "test"]}
  :profiles {:midje {:dependencies [[midje "1.9.1"]]
                     :plugins      [[lein-midje "3.2.1"]
                                    [midje-readme "1.0.9"]]
                     :midje-readme {:require "[honeysql.core :as sql]
                                              [honeysql.helpers :refer :all :as helpers]
                                              [honeysql.format :as fmt]
                                              [honeysql.helpers :refer [defhelper]]"}}
             :dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.9.521"]
                                  [cljsbuild "1.1.7"]]
                   :plugins [[lein-cljsbuild "1.1.7"]
                             [jonase/eastwood "0.2.6"]
                             [lein-doo "0.1.10"]
                             [lein-tach "1.0.0"]]}})
