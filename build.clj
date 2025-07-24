(ns build
  "HoneySQL's build script.

  clojure -T:build jar
  clojure -T:build deploy

  Run tests:
  bb test

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

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
     [:name "Eclipse Public License"]
     [:url "http://www.eclipse.org/legal/epl-v10.html"]]]
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

(defn jar "Build the JAR." [opts]
  (let [opts (jar-opts opts)]
    (b/delete {:path "target"})
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
    (println "\nBuilding" (:jar-file opts) "...")
    (b/jar opts))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
