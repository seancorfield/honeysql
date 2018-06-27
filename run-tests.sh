#!/bin/sh
#
# Assumes node.js/npm and lumo-cljs are installed!
# See .travis.yml for details of the test environment.
#
rm -rf test/readme.clj
if test "$1" = "all"
then
  clj_test="test-all"
else
  clj_test="test"
fi
lein do clean, check, eastwood, $clj_test, tach lumo, test-readme
