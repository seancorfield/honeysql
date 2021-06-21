#!/bin/sh
set -Eeo pipefail

echo ==== Test README.md ==== && clojure -M:readme && \
  echo ==== Lint Source ==== && clojure -M:eastwood && \
  echo ==== Test ClojureScript ==== && clojure -M:test:cljs-runner

if test $? -eq 0
then
  if test "$1" = "all"
  then
    for v in 1.9 1.10 master
    do
      echo ==== Test Clojure $v ====
      clojure -X:test:$v
      if test $? -ne 0
      then
        exit 1
      fi
    done
  else
    echo ==== Test Clojure ====
    clojure -X:test
  fi
else
  exit 1
fi
