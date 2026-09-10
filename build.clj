(ns build
  "HoneySQL's build script. Entirely driven by `bb`."
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [babashka.deps-deploy :as dd]))

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

(defn parse-args "Parse 'cljs', 'all', and '1.x' versions from the command line."
  []
  (let [cljs?    (some #{"cljs"} *command-line-args*)
        all?     (some #{"all"} *command-line-args*)
        versions (or (seq (filter (fn [v] (str/starts-with? v "1."))
                                  *command-line-args*))
                     ["1.10"])]
    [cljs? (if all? ["elide" "1.11" "1.12" "1.13" "cljs"] versions)]))

(defn testing-str "Return a string indicating the current testing context."
  [v cljs?]
  (str "Testing "
       (cond (= v "cljs") "ClojureScript"
             cljs?
             (str "Clojure " v " and ClojureScript")
             :else
             (str "Clojure " v))))
