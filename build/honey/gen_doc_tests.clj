(ns honey.gen-doc-tests
  (:require [babashka.fs :as fs]
            [lread.test-doc-blocks :as tdb]))

(defn -main [& _args]
  (let [target "target/test-doc-blocks"
        success-marker (fs/file target "SUCCESS")
        docs ["README.md"
              "doc/clause-reference.md"
              "doc/differences-from-1-x.md"
              "doc/extending-honeysql.md"
              "doc/general-reference.md"
              "doc/getting-started.md"
              "doc/postgresql.md"
              "doc/special-syntax.md"]
        regen-reason (if (not (fs/exists? success-marker))
                       "a previous successful gen result not found"
                       (let [newer-thans (fs/modified-since target
                                                            (concat docs
                                                                    ["build.clj" "deps.edn"]
                                                                    (fs/glob "build" "**/*.*")
                                                                    (fs/glob "src" "**/*.*")))]
                         (when (seq newer-thans)
                           (str "found files newer than last gen: " (mapv str newer-thans)))))]
    (if regen-reason
      (do
        (fs/delete-if-exists success-marker)
        (println "gen-doc-tests: Regenerating:" regen-reason)
        (tdb/gen-tests {:docs docs})
        (spit success-marker "SUCCESS"))
      (println "gen-doc-tests: Tests already successfully generated"))))
