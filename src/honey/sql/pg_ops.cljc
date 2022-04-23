;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.sql.pg-ops
  "Register all the PostgreSQL JSON/JSONB operators
  and provide convenient Clojure names for those ops.
  In addition, provide names for the PostgreSQL
  regex operators as well.

  For the eleven that cannot be written directly as
  symbols, use mnemonic names: hash for #, at for @,
  and tilde for ~.

  For the six of those that cannot be written as
  keywords, invoke the `keyword` function instead.

  Those latter eight (`at>`, `<at`, `at?`, `atat`,
  `tilde`, `tilde*`, `!tilde`, and `!tilde*`) are
  the only ones that should really be needed in the
  DSL. The other names are provided for completeness.

  `regex` and `iregex` are provided as aliases for the
  regex operators `tilde` and `tilde*` respectively.
  `!regex` and `!iregex` are provided as aliases for the
  regex operators `!tilde` and `!tilde*` respectively."
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

(def tilde   "The case-sensitive regex match operator."   (keyword "~"))
(def tilde*  "The case-insensitive regex match operator." (keyword "~*"))
(def !tilde  "The case-sensitive regex unmatch operator."   (keyword "!~"))
(def !tilde* "The case-insensitive regex unmatch operator." (keyword "!~*"))
;; aliases:
(def regex   tilde)
(def iregex  tilde*)
(def !regex  !tilde)
(def !iregex !tilde*)

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

(sql/register-op! tilde)
(sql/register-op! tilde*)
(sql/register-op! !tilde)
(sql/register-op! !tilde*)
