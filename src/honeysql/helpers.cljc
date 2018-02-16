(ns honeysql.helpers
  (:refer-clojure :exclude [update])
  #?(:clj (:require [net.cgrand.macrovich :as macros])
     :cljs (:require-macros [net.cgrand.macrovich :as macros]
                            [honeysql.helpers :refer [defhelper]])))

(defmulti build-clause (fn [name & args]
                         name))

(defmethod build-clause :default [_ m & args]
  m)

(defn plain-map? [m]
  (and
    (map? m)
    (not (record? m))))

(macros/deftime
  (defmacro defhelper [helper arglist & more]
    (when-not (vector? arglist)
      (throw #?(:clj (IllegalArgumentException. "arglist must be a vector")
                :cljs (js/Error. "arglist must be a vector"))))
    (when-not (= (count arglist) 2)
      (throw #?(:clj (IllegalArgumentException. "arglist must have two entries, map and varargs")
                :cljs (js/Error. "arglist must have two entries, map and varargs"))))

    (let [kw (keyword (name helper))
          [m-arg varargs] arglist]
      `(do
         (defmethod build-clause ~kw ~['_ m-arg varargs] ~@more)
         (defn ~helper [& args#]
           (let [[m# args#] (if (plain-map? (first args#))
                              [(first args#) (rest args#)]
                              [{} args#])]
             (build-clause ~kw m# args#)))

         ;; maintain the original arglist instead of getting
         ;; ([& args__6880__auto__])
         (alter-meta!
          (var ~helper)
          assoc
          :arglists
          '(~['& varargs]
            ~[m-arg '& varargs]))))))

(defn collify [x]
  (if (coll? x) x [x]))

(macros/usetime
 (defhelper select [m fields]
   (assoc m :select (collify fields))))

(macros/usetime
 (defhelper merge-select [m fields]
   (update-in m [:select] concat (collify fields))))

(macros/usetime
 (defhelper un-select [m fields]
   (update-in m [:select] #(remove (set (collify fields)) %))))

(macros/usetime
 (defhelper from [m tables]
   (assoc m :from (collify tables))))

(macros/usetime
 (defhelper merge-from [m tables]
   (update-in m [:from] concat (collify tables))))

(defmethod build-clause :where [_ m pred]
  (if (nil? pred)
    m
    (assoc m :where pred)))

(defn- prep-where [args]
  (let [[m preds] (if (map? (first args))
                    [(first args) (rest args)]
                    [{} args])
        [logic-op preds] (if (keyword? (first preds))
                           [(first preds) (rest preds)]
                           [:and preds])
        pred (if (= 1 (count preds))
               (first preds)
               (into [logic-op] preds))]
    [m pred logic-op]))

(defn where [& args]
  (let [[m pred] (prep-where (remove nil? args))]
    (if (nil? pred)
      m
      (assoc m :where pred))))

(defmethod build-clause :merge-where [_ m pred]
  (if (nil? pred)
    m
    (assoc m :where (if (not (nil? (:where m)))
                      [:and (:where m) pred]
                      pred))))

(defn merge-where [& args]
  (let [[m pred logic-op] (prep-where args)]
    (if (nil? pred)
      m
      (assoc m :where (if (not (nil? (:where m)))
                        [logic-op (:where m) pred]
                        pred)))))

(macros/usetime
 (defhelper join [m clauses]
   (assoc m :join clauses)))

(macros/usetime
 (defhelper merge-join [m clauses]
   (update-in m [:join] concat clauses)))

(macros/usetime
 (defhelper left-join [m clauses]
   (assoc m :left-join clauses)))

(macros/usetime
 (defhelper merge-left-join [m clauses]
   (update-in m [:left-join] concat clauses)))

(macros/usetime
 (defhelper right-join [m clauses]
   (assoc m :right-join clauses)))

(macros/usetime
 (defhelper merge-right-join [m clauses]
   (update-in m [:right-join] concat clauses)))

(macros/usetime
 (defhelper full-join [m clauses]
   (assoc m :full-join clauses)))

(macros/usetime
 (defhelper merge-full-join [m clauses]
   (update-in m [:full-join] concat clauses)))

(defmethod build-clause :group-by [_ m fields]
  (assoc m :group-by (collify fields)))

(defn group [& args]
  (let [[m fields] (if (map? (first args))
                     [(first args) (rest args)]
                     [{} args])]
    (build-clause :group-by m fields)))

(macros/usetime
 (defhelper merge-group-by [m fields]
   (update-in m [:group-by] concat (collify fields))))

(defmethod build-clause :having [_ m pred]
  (if (nil? pred)
    m
    (assoc m :having pred)))

(defn having [& args]
  (let [[m pred] (prep-where args)]
    (if (nil? pred)
      m
      (assoc m :having pred))))

(defmethod build-clause :merge-having [_ m pred]
  (if (nil? pred)
    m
    (assoc m :having (if (not (nil? (:having m)))
                       [:and (:having m) pred]
                       pred))))

(defn merge-having [& args]
  (let [[m pred logic-op] (prep-where args)]
    (if (nil? pred)
      m
      (assoc m :having (if (not (nil? (:having m)))
                         [logic-op (:having m) pred]
                         pred)))))

(macros/usetime
 (defhelper order-by [m fields]
   (assoc m :order-by (collify fields))))

(macros/usetime
 (defhelper merge-order-by [m fields]
   (update-in m [:order-by] concat (collify fields))))

(macros/usetime
 (defhelper limit [m l]
   (if (nil? l)
     m
     (assoc m :limit (if (coll? l) (first l) l)))))

(macros/usetime
 (defhelper offset [m o]
   (if (nil? o)
     m
     (assoc m :offset (if (coll? o) (first o) o)))))

(macros/usetime
 (defhelper lock [m lock]
   (cond-> m
     lock
     (assoc :lock lock))))

(macros/usetime
 (defhelper modifiers [m ms]
   (if (nil? ms)
     m
     (assoc m :modifiers (collify ms)))))

(macros/usetime
 (defhelper merge-modifiers [m ms]
   (if (nil? ms)
     m
     (update-in m [:modifiers] concat (collify ms)))))

(defmethod build-clause :insert-into [_ m table]
  (assoc m :insert-into table))

(defn insert-into
  ([table] (insert-into nil table))
  ([m table] (build-clause :insert-into m table)))

(macros/usetime
 (defhelper columns [m fields]
   (assoc m :columns (collify fields))))

(macros/usetime
 (defhelper merge-columns [m fields]
   (update-in m [:columns] concat (collify fields))))

(defmethod build-clause :values [_ m vs]
  (assoc m :values vs))

(defn values
  ([vs] (values nil vs))
  ([m vs] (build-clause :values m vs)))

(defmethod build-clause :merge-values [_ m vs]
  (update-in m [:values] concat vs))

(defn merge-values
  ([vs] (merge-values nil vs))
  ([m vs] (build-clause :merge-values m vs)))

(defmethod build-clause :query-values [_ m vs]
  (assoc m :query-values vs))

(defn query-values
  ([vs] (values nil vs))
  ([m vs] (build-clause :query-values m vs)))

(defmethod build-clause :update [_ m table]
  (assoc m :update table))

(defn update
  ([table] (update nil table))
  ([m table] (build-clause :update m table)))

(defmethod build-clause :set [_ m values]
  (assoc m :set values))

;; short for sql set, to avoid name collision with clojure.core/set
(defn sset
  ([vs] (values nil vs))
  ([m vs] (build-clause :set m vs)))

(defmethod build-clause :delete-from [_ m table]
  (assoc m :delete-from table))

(defn delete-from
  ([table] (delete-from nil table))
  ([m table] (build-clause :delete-from m table)))

(macros/usetime
 (defhelper with [m ctes]
   (assoc m :with ctes)))

(macros/usetime
 (defhelper with-recursive [m ctes]
   (assoc m :with-recursive ctes)))

(defmethod build-clause :union [_ m maps]
  (assoc m :union maps))

(defmethod build-clause :union-all [_ m maps]
  (assoc m :union-all maps))

(defmethod build-clause :intersect [_ m maps]
  (assoc m :intersect maps))
