#!/bin/sh
#
# Assumes node.js/npm and lumo-cljs are installed!
# See .travis.yml for details of the test environment.
#
rm -rf test/readme.clj
lein do clean, check, eastwood, test, tach lumo, test-readme
