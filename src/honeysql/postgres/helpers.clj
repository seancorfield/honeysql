(ns honeysql.postgres.helpers
  (:refer-clojure :exclude [update])
  (:require [honeysql.helpers :refer :all]))

(defn do-nothing [m]
  (assoc m :do-nothing []))

(defhelper do-update-set [m args]
  (assoc m :do-update-set (collify args)))

(defhelper db-update-set! [m args]
  (assoc m :do-update-set! args))

(defhelper on-conflict [m args]
  (assoc m :on-conflict args))

(defhelper on-conflict-constraint [m args]
  (assoc m :on-conflict-constraint args))

(defhelper upsert [m args]
  (if (plain-map? args)
    (assoc m :upsert args)
    (assoc m :upsert (first args))))

(defhelper returning [m fields]
  (assoc m :returning (collify fields)))
