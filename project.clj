(defproject honeysql "0.9.4"
  :description "SQL as Clojure data structures"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/jkk/honeysql"
  :scm {:name "git"
        :url "https://github.com/jkk/honeysql"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [net.cgrand/macrovich "0.2.1"]]
  :aliases {"test-readme" ["with-profile" "midje" "midje"]
            "test-all" ["with-profile"
                         "default:1.7,default:1.8,default:1.9,default:1.10,default:master"
                         "test"]}
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
  :profiles {:midje {:dependencies [[midje "1.9.6"]]
                     :plugins      [[lein-midje "3.2.1"]
                                    [midje-readme "1.0.9"]]
                     :midje-readme {:require "[honeysql.core :as sql]
                                              [honeysql.helpers :refer :all :as helpers]
                                              [honeysql.format :as fmt]
                                              [honeysql.helpers :refer [defhelper]]"}}
             :master {:repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]]
                      :dependencies [[org.clojure/clojure "1.11.0-master-SNAPSHOT"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :dev {:dependencies [[org.clojure/clojurescript "1.10.520"]
                                  [cljsbuild "1.1.7"]]
                   :plugins [[lein-cljsbuild "1.1.7"]
                             [jonase/eastwood "0.3.5"]
                             [lein-doo "0.1.11"]
                             [lein-tach "1.0.0"]]}})
