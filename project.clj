(defproject honeysql "0.7.1-SNAPSHOT"
  :description "SQL as Clojure data structures"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/jkk/honeysql"
  :scm {:name "git"
        :url "https://github.com/jkk/honeysql"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.9.89"]]
                   :cljsbuild {:builds [{:source-paths ["src" "test"]}]}
                   :plugins [[lein-cljsbuild "1.0.6"]]}})
