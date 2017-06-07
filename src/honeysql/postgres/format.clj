(ns honeysql.postgres.format
  (:refer-clojure :exclude [format])
  (:require [honeysql.format :refer :all]))

;; Move the whole default priorities here ?
(def postgres-clause-priorities
  {:upsert 225
   :on-conflict 230
   :on-conflict-constraint 230
   :do-update-set 235
   :do-update-set! 235
   :do-nothing 235
   :returning 240
   :query-values 250})

;; FIXME : Not sure if this is the best way to implement this, but since the `clause-store` is being used
;; by format to decide the order of clauses, not really sure what would be a better implementation.
(doseq [[k v] postgres-clause-priorities]
  (register-clause! k v))

(defn get-first [x]
  (if (coll? x)
    (first x)
    x))

(defmethod format-clause :on-conflict-constraint [[_ k] _]
  (str "ON CONFLICT ON CONSTRAINT " (-> k
                                        get-first
                                        to-sql)))

(defmethod format-clause :on-conflict [[_ id] _]
  (str "ON CONFLICT (" (-> id
                           get-first
                           to-sql) ")"))

(defmethod format-clause :do-nothing [_ _]
  "DO NOTHING")

;; Used when there is a need to update the columns with modified values if the
;; row(id) already exits - accepts a map of column and value
(defmethod format-clause :do-update-set! [[_ values] _]
  (str "DO UPDATE SET " (comma-join (for [[k v] values]
                                      (str (to-sql k) " = " (to-sql v))))))

;; Used when it is a simple upsert - accepts a vector of columns
(defmethod format-clause :do-update-set [[_ values] _]
  (str "DO UPDATE SET "
       (comma-join (map #(str (to-sql %) " = EXCLUDED." (to-sql %))
                        values))))

(defn format-upsert-clause [upsert]
  (let [ks (keys upsert)]
    (map #(format-clause % (find upsert %)) upsert)))

;; Accepts a map with the following possible keys
;; :on-conflict, :do-update-set or :do-update-set! or :do-nothing
(defmethod format-clause :upsert [[_ upsert] _]
  (space-join (format-upsert-clause upsert)))

(defmethod format-clause :returning [[_ fields] _]
  (str "RETURNING " (comma-join (map to-sql fields))))
