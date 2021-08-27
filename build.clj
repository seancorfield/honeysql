(ns build
  "HoneySQL's build script.

  clojure -T:build run-tests
  clojure -T:build run-tests :aliases '[:master]'

  clojure -T:build ci

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:require [babashka.fs :as fs]
            [clojure.tools.build.api :as b]
            [clojure.tools.deps.alpha :as t]
            [deps-deploy.deps-deploy :as dd]
            [lread.test-doc-blocks :as tdb]))

(def lib 'com.github.seancorfield/honeysql)
(def version (format "2.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean "Remove the target folder." [_]
  (println "\nCleaning target...")
  (b/delete {:path "target"}))

(defn jar "Build the library JAR file." [_]
  (println "\nWriting pom.xml...")
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :scm {:tag (str "v" version)}
                :basis basis
                :src-dirs ["src"]})
  (println "Copying src...")
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (println (str "Building jar " jar-file "..."))
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn- run-task
  [aliases]
  (println "\nRunning task for:" aliases)
  (let [basis    (b/create-basis {:aliases aliases})
        combined (t/combine-aliases basis aliases)
        cmds     (b/java-command {:basis     basis
                                  :java-opts (:jvm-opts combined)
                                  :main      'clojure.main
                                  :main-args (:main-opts combined)})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit)
      (throw (ex-info (str "Task failed for: " aliases) {})))))

(defn gen-doc-tests "Generate tests from doc code blocks" [opts]
  (let [docs ["README.md"
              "doc/clause-reference.md"
              "doc/differences-from-1-x.md"
              "doc/extending-honeysql.md"
              "doc/general-reference.md"
              "doc/getting-started.md"
              "doc/postgresql.md"
              "doc/special-syntax.md"]
        updated-docs (fs/modified-since "target/test-doc-blocks" (conj docs "build.clj" "deps.edn"))]
    (if (seq updated-docs)
      (do
        (println "gen-doc-tests: Regenerating: found newer:" (mapv str updated-docs))
        (tdb/gen-tests {:docs docs}))
      (println "gen-doc-tests: Tests already generated")))
  opts)

(defn eastwood "Run Eastwood." [opts] (run-task [:eastwood]) opts)

(defn run-tests
  "Run regular tests.

  Optionally specify :aliases:
  [:1.9] -- test against Clojure 1.9 (the default)
  [:1.10] -- test against Clojure 1.10.3
  [:master] -- test against Clojure 1.11 master snapshot
  [:cljs] -- test against ClojureScript"
  [{:keys [aliases] :as opts}]
  (run-task (into [:test] aliases))
  opts)

(defn run-doc-tests
  "Run generated doc tests.

  Optionally specify :platform:
  :1.9 -- test against Clojure 1.9 (the default)
  :1.10 -- test against Clojure 1.10.3
  :master -- test against Clojure 1.11 master snapshot
  :cljs -- test against ClojureScript"
  [{:keys [platform] :or {platform :1.9} :as opts}]
  (gen-doc-tests opts)
  (let [aliases (case platform
                  :cljs [platform :test-doc :test-doc-cljs]
                  [platform :runner :test-doc :test-doc-clj])]
    (run-task aliases))
  opts)

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (clean)
      (as-> opts
          (reduce (fn [opts platform]
                    (run-doc-tests (assoc opts :platform platform)))
                  opts
                  [:cljs :1.9 :1.10 :master]))
      (eastwood)
      (as-> opts
            (reduce (fn [opts alias]
                      (run-tests (assoc opts :aliases (cond-> [alias]
                                                        (not= :cljs alias)
                                                        (conj :runner)))))
                    opts
                    [:cljs :1.9 :1.10 :master]))
      (clean)
      (jar)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (dd/deploy (merge {:installer :remote :artifact jar-file
                     :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                    opts)))
