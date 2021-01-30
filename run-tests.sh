#!/bin/sh

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
      clojure -M:test:runner:$v
      if test $? -ne 0
      then
        exit 1
      fi
    done
  else
    echo ==== Test Clojure ====
    clojure -M:test:runner
  fi
else
  exit 1
fi
