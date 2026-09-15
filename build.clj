(ns build
  "HoneySQL's build script. Entirely driven by `bb`."
  (:require [babashka.deps-deploy :as dd]
            [babashka.tasks :refer [shell]]
            [clojure.tools.build.api :as b]))

(def lib 'com.github.seancorfield/honeysql)
(defn- the-version [patch] (format "2.7.%s" patch))
(def version (the-version (b/git-count-revs nil)))
(def snapshot (the-version "9999-SNAPSHOT"))
(def class-dir "target/classes")

(defn- pom-template [version]
  [[:description "SQL as Clojure data structures."]
   [:url "https://github.com/seancorfield/honeysql"]
   [:licenses
    [:license
     [:name "Eclipse Public License 2.0"]
     [:url "https://www.eclipse.org/legal/epl-2.0"]]]
   [:developers
    [:developer
     [:name "Sean Corfield"]]
    [:developer
     [:name "Justin Kramer"]]]
   [:scm
    [:url "https://github.com/seancorfield/honeysql"]
    [:connection "scm:git:https://github.com/seancorfield/honeysql.git"]
    [:developerConnection "scm:git:ssh:git@github.com:seancorfield/honeysql.git"]
    [:tag (str "v" version)]]])

(defn- jar-opts [opts]
  (let [version (if (:snapshot opts) snapshot version)]
    (println "\nVersion:" version)
    (assoc opts
           :lib lib   :version version
           :jar-file  (format "target/%s-%s.jar" lib version)
           :basis     (b/create-basis {})
           :class-dir class-dir
           :target    "target"
           :src-dirs  ["src"]
           :pom-data  (pom-template version))))

(defn jar "Build the JAR."
  {:org.babashka/cli {:spec {:snapshot {:coerce :boolean}}}}
  [opts]
  (let [opts (jar-opts opts)]
    (b/delete {:path "target"})
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
    (println "\nBuilding" (:jar-file opts) "...")
    (b/jar opts))
  opts)

(defn deploy "Deploy the JAR to Clojars."
  {:org.babashka/cli {:spec {:snapshot {:coerce :boolean}}}}
  [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)

;; test-related utilities and tasks:

(defn- get-versions [opts]
  (if (:all-versions opts) ["elide" "1.11" "1.12" "1.13" "cljs"] ["1.10"]))

(defn- testing-str [v]
  (str "Testing " (if (= v "cljs") "ClojureScript" (str "Clojure " v))))

(defn run-doc-tests "Run documentation tests for the specified Clojure versions."
  {:org.babashka/cli {:spec {:all-versions {:coerce :boolean}}}}
  [opts]
  (let [versions (get-versions opts)]
    (doseq [v versions]
      (println (str "\nDoc-" (testing-str v)))
      (shell (str "clojure -M:test:test-doc"
                  ":" v
                  (if (= "cljs" v)
                    ":test-doc-cljs"
                    ":test-doc-clj"))))))

(defn run-tests "Run tests for the specified Clojure versions."
  {:org.babashka/cli {:spec {:all-versions {:coerce :boolean}}}}
  [opts]
  (let [versions (get-versions opts)]
    (doseq [v versions]
      (println (str "\n" (testing-str v)))
      (shell (str "clojure -M"
                  ":" v
                  ":test:"
                  (if (= "cljs" v)
                    "cljs-runner"
                    "runner"))))))

;; low-level tasks:

(defn eastwood [_] (shell "clojure -M:eastwood"))
(defn gen-doc-tests [_] (shell "clojure -M:gen-doc-tests"))
