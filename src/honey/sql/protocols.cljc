;; copyright (c) 2022-2024 sean corfield, all rights reserved

(ns honey.sql.protocols
  "InlineValue -- a protocol that defines how to inline
    values; (sqlize x) produces a SQL string for x.")

#?(:lg () :clj (set! *warn-on-reflection* true))

#?(:lg
   (defprotocol InlineValue
                (sqlize [this] "Render value inline in a SQL string."))
   :clj
   (defprotocol InlineValue :extend-via-metadata true
                (sqlize [this] "Render value inline in a SQL string.")))
