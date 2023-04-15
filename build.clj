(ns build
  "HoneySQL's build script.

  clojure -T:build ci

  clojure -T:build run-doc-tests :aliases '[:cljs]'

  Run tests:
  clojure -X:test
  clojure -X:test:master

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.tools.deps :as t]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.github.seancorfield/honeysql)
(defn- the-version [patch] (format "2.4.%s" patch))
(def version (the-version (b/git-count-revs nil)))
(def snapshot (the-version "9999-SNAPSHOT"))
(def class-dir "target/classes")

(defn- run-task [aliases]
  (println "\nRunning task for" (str/join "," (map name aliases)))
  (let [basis    (b/create-basis {:aliases aliases})
        combined (t/combine-aliases basis aliases)
        cmds     (b/java-command
                  {:basis basis
                   :java-opts (:jvm-opts combined)
                   :main      'clojure.main
                   :main-args (:main-opts combined)})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Task failed" {})))))

(defn eastwood "Run Eastwood." [opts]
  (run-task [:eastwood])
  opts)

(defn gen-doc-tests "Generate tests from doc code blocks." [opts]
  (run-task [:gen-doc-tests])
  opts)

(defn run-doc-tests
  "Generate and run doc tests.

  Optionally specify :aliases vector:
  [:1.9] -- test against Clojure 1.9 (the default)
  [:1.10] -- test against Clojure 1.10.3
  [:1.11] -- test against Clojure 1.11.0
  [:master] -- test against Clojure 1.12 master snapshot
  [:cljs] -- test against ClojureScript"
  [{:keys [aliases] :as opts}]
  (gen-doc-tests opts)
  (run-task (-> [:test :runner :test-doc]
                (into aliases)
                (into (if (some #{:cljs} aliases)
                        [:test-doc-cljs]
                        [:test-doc-clj]))))
  opts)

(defn test "Run basic tests." [opts]
  (run-task [:test :runner :1.11])
  opts)

(defn- jar-opts [opts]
  (let [version (if (:snapshot opts) snapshot version)]
    (println "\nVersion:" version)
    (assoc opts
           :lib lib :version version
           :jar-file (format "target/%s-%s.jar" lib version)
           :scm {:tag (str "v" version)}
           :basis (b/create-basis {})
           :class-dir class-dir
           :target "target"
           :src-dirs ["src"]
           :src-pom "template/pom.xml")))

(defn ci
  "Run the CI pipeline of tests (and build the JAR).

  Default Clojure version is 1.9.0 (:1.9) so :elide
  tests for #409 on that version."
  [opts]
  (let [aliases [:cljs :elide :1.10 :1.11 :master]
        opts    (jar-opts opts)]
    (b/delete {:path "target"})
    (doseq [alias aliases]
      (run-doc-tests {:aliases [alias]}))
    (eastwood opts)
    (doseq [alias aliases]
      (run-task [:test :runner alias]))
    (b/delete {:path "target"})
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
    (println "\nBuilding JAR...")
    (b/jar opts))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
