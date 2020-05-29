#!/bin/sh

echo ==== Test README.md ==== && clojure -A:readme && \
  echo ==== Lint Source ==== && clojure -A:eastwood && \
  echo ==== Test ClojureScript ==== && clojure -A:test:cljs-runner

if test $? -eq 0
then
  if test "$1" = "all"
  then
    for v in 1.7 1.8 1.9 1.10 master
    do
      echo ==== Test Clojure $v ====
      clojure -A:test:runner:$v
      if test $? -ne 0
      then
        exit 1
      fi
    done
  else
    echo ==== Test Clojure ====
    clojure -A:test:runner
  fi
else
  exit 1
fi
