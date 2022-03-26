;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.sql.pg-json
  "Register all the PostgreSQL JSON/JSONB operators
  and provide convenient Clojure names for those ops.

  For the seven that cannot be written directly as
  symbols, use mnemonic names: hash for # and at for @.

  For the four of those that cannot be written as
  keywords, invoke the `keyword` function instead.

  Those latter four (`at>`, `<at`, `at?`, and `atat`)
  are the only ones that should really be needed in the
  DSL. The other names are provided for completeness."
  (:refer-clojure :exclude [-> ->> -])
  (:require [honey.sql :as sql]))

;; see https://www.postgresql.org/docs/current/functions-json.html

(def ->     "The -> operator."  :->)
(def ->>    "The ->> operator." :->>)
(def hash>  "The #> operator."  :#>)
(def hash>> "The #>> operator." :#>>)
(def at>    "The @> operator."  (keyword "@>"))
(def <at    "The <@ operator."  (keyword "<@"))
(def ?      "The ? operator."   :?)
(def ?|     "The ?| operator."  :?|)
(def ?&     "The ?& operator."  :?&)
(def ||     "The || operator."  :||)
(def -      "The - operator."   :-)
(def hash-  "The #- operator."  :#-)
(def at?    "The @? operator."  (keyword "@?"))
(def atat   "The @@ operator."  (keyword "@@"))

(sql/register-op! :-> :variadic true)
(sql/register-op! :->>)
(sql/register-op! :#>)
(sql/register-op! :#>>)
(sql/register-op! at>)
(sql/register-op! <at)
(sql/register-op! :?)
(sql/register-op! :?|)
(sql/register-op! :?&)
;; these are already known operators:
;(sql/register-op! :||)
;(sql/register-op! :-)
(sql/register-op! :#-)
(sql/register-op! at?)
(sql/register-op! atat)
