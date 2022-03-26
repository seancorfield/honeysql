;; copyright (c) 2022 sean corfield, all rights reserved

(ns honey.sql.pg-json
  "Register all the PostgreSQL JSON/JSONB operators
  and provide convenient Clojure names for those ops
  that cannot be written as keywords or symbols directly."
  (:require [honey.sql :as sql]))

;; see https://www.postgresql.org/docs/current/functions-json.html

;; :->
;; :->>
;; :#>
;; :#>>
(def at> "The @> operator." (keyword "@>"))
(def <at "The <@ operator." (keyword "<@"))
;; :?
;; :?|
;; :?&
;; :||
;; :-
;; :#-
(def at? "The @? operator." (keyword "@?"))
(def atat "The @@ operator." (keyword "@@"))

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
