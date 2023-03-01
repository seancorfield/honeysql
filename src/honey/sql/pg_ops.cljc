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

(def ->
  "The -> operator for accessing nested JSON(B) values as JSON(B).
  Ex.: 
  ```clojure
  (sql/format {:select [[[:->> [:-> :my_column \"kids\" [:inline 0]] \"name\"]]]})
  ; => [\"SELECT (my_column -> ? -> 0) ->> ?\" \"kids\" \"name\"]
  ```
  
  Notice we need to wrap the keys/indices with :inline if we don't want them to become parameters."
  :->)
(def ->>    "The ->> operator - like -> but returns the value as text instead of a JSON object." :->>)
(def hash>  "The #> operator extracts JSON sub-object at the specified path."  :#>)
(def hash>> "The #>> operator - like hash> but returns the value as text instead of JSON object." :#>>)
(def at>    "The @> operator - does the first JSON value contain the second?"  (keyword "@>"))
(def <at    "The <@ operator - is the first JSON value contained in the second?"  (keyword "<@"))
(def ?      "The ? operator - does the text string exist as a top-level key or array element within the JSON value?"   :?)
(def ?|     "The ?| operator - do any of the strings in the text array exist as top-level keys or array elements?"  :?|)
(def ?&     "The ?& operator - do all of the strings in the text array exist as top-level keys or array elements?"  :?&)
(def ||     "The || operator - concatenates two jsonb values (arrays or objects; anything else treated as 1-element array)."  :||)
(def -
  "The - operator: 
   - text value: deletes a key (and its value) from a JSON object, or matching string value(s) from a JSON array
   - int value: deletes the array element with specified index (negative integers count from the end)"
  :-)
(def hash-  "The #- operator - deletes the field or array element at the specified path, where path elements can be either field keys or array indexes."  :#-)
(def at?    "The @? operator - does JSON path return any item for the specified JSON value?"  (keyword "@?"))
(def atat
  "The @@ operator - returns the result of a JSON path predicate check for the specified JSON value. 
  Only the first item of the result is taken into account. If the result is not Boolean, then NULL is returned."  
  (keyword "@@"))

(def tilde   "The case-sensitive regex match operator."   (keyword "~"))
(def tilde*  "The case-insensitive regex match operator." (keyword "~*"))
(def !tilde  "The case-sensitive regex unmatch operator."   (keyword "!~"))
(def !tilde* "The case-insensitive regex unmatch operator." (keyword "!~*"))
;; aliases:
(def regex   tilde)
(def iregex  tilde*)
(def !regex  !tilde)
(def !iregex !tilde*)

(sql/register-op! :->)
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
