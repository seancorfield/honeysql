(ns honeysql.helpers
  (:refer-clojure :exclude [update])
  #?(:cljs (:require-macros [honeysql.helpers :refer [defhelper]])))

(defmulti build-clause (fn [name & args]
                         name))

(defmethod build-clause :default [_ m & args]
  m)

(defn plain-map? [m]
  (and
    (map? m)
    (not (record? m))))

#?(:clj
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

(defhelper select [m fields]
  (assoc m :select (collify fields)))

(defhelper merge-select [m fields]
  (update-in m [:select] concat (collify fields)))

(defhelper un-select [m fields]
  (update-in m [:select] #(remove (set (collify fields)) %)))

(defhelper from [m tables]
  (assoc m :from (collify tables)))

(defhelper merge-from [m tables]
  (update-in m [:from] concat (collify tables)))

(defmethod build-clause :where [_ m pred]
  (if (nil? pred)
    m
    (assoc m :where pred)))

(defn- merge-where-args
  "Handle optional args passed to `merge-where` or similar functions. Returns tuple of

    [m where-clauses conjunction-operator]"
  [args]
  (let [[m & args]              (if (map? (first args))
                                  args
                                  (cons {} args))
        [conjunction & clauses] (if (keyword? (first args))
                                  args
                                  (cons :and args))]
    [m (filter some? clauses) conjunction]))

(defn- where-args
  "Handle optional args passed to `where` or similar functions. Merges clauses together. Returns tuple of

    [m merged-clause]"
  [args]
  (let [[m clauses conjunction] (merge-where-args args)]
    [m (if (<= (count clauses) 1)
         (first clauses)
         (into [conjunction] clauses))]))

(defn- where-like
  "Create a WHERE-style clause with key `k` (e.g. `:where` or `:having`)"
  [k args]
  (let [[m pred] (where-args args)]
    (if (nil? pred)
      m
      (assoc m k pred))))

(defn where [& args]
  (where-like :where args))

(defn- is-clause? [clause x]
  (and (sequential? x) (= (first x) clause)))

(defn- merge-where-like
  "Merge a WHERE-style clause with key `k` (e.g. `:where` or `:having`)"
  [k args]
  (let [[m new-clauses conjunction] (merge-where-args args)]
    (reduce
     (fn [m new-clause]
       ;; combine existing clause and new clause if they're both of the specified conjunction type, e.g.
       ;; [:and a b] + [:and c d] -> [:and a b c d]
       (update-in m [k] (fn [existing-clause]
                          (let [existing-subclauses (when (some? existing-clause)
                                                      (if (is-clause? conjunction existing-clause)
                                                        (rest existing-clause)
                                                        [existing-clause]))
                                new-subclauses      (if (is-clause? conjunction new-clause)
                                                      (rest new-clause)
                                                      [new-clause])
                                subclauses          (concat existing-subclauses new-subclauses)]
                            (if (> (count subclauses) 1)
                              (into [conjunction] subclauses)
                              (first subclauses))))))
     m
     new-clauses)))

(defn merge-where
  "Merge a series of `where-clauses` together. Supports two optional args: a map to merge the results into, and a
  `conjunction` to use to combine clauses (defaults to `:and`).

    (merge-where [:= :x 1] [:= :y 2])
    {:where [:and [:= :x 1] [:= :y 2]]}

    (merge-where {:where [:= :x 1]} [:= :y 2])
    ;; -> {:where [:and [:= :x 1] [:= :y 2]]}

    (merge-where :or [:= :x 1] [:= :y 2])
    ;; -> {:where [:or [:= :x 1] [:= :y 2]]}"
  {:arglists '([& where-clauses]
               [m-or-conjunction & where-clauses]
               [m conjunction & where-clauses])}
  [& args]
  (merge-where-like :where args))

(defmethod build-clause :merge-where
  [_ m where-clause]
  (merge-where m where-clause))

(defhelper join [m clauses]
  (assoc m :join clauses))

(defhelper merge-join [m clauses]
  (update-in m [:join] concat clauses))

(defhelper left-join [m clauses]
  (assoc m :left-join clauses))

(defhelper merge-left-join [m clauses]
  (update-in m [:left-join] concat clauses))

(defhelper right-join [m clauses]
  (assoc m :right-join clauses))

(defhelper merge-right-join [m clauses]
  (update-in m [:right-join] concat clauses))

(defhelper full-join [m clauses]
  (assoc m :full-join clauses))

(defhelper merge-full-join [m clauses]
  (update-in m [:full-join] concat clauses))

(defhelper cross-join [m clauses]
  (assoc m :cross-join clauses))

(defhelper merge-cross-join [m clauses]
  (update-in m [:cross-join] concat clauses))

(defmethod build-clause :group-by [_ m fields]
  (assoc m :group-by (collify fields)))

(defn group [& args]
  (let [[m fields] (if (map? (first args))
                     [(first args) (rest args)]
                     [{} args])]
    (build-clause :group-by m fields)))

(defhelper merge-group-by [m fields]
  (update-in m [:group-by] concat (collify fields)))

(defmethod build-clause :having [_ m pred]
  (if (nil? pred)
    m
    (assoc m :having pred)))

(defn having [& args]
  (where-like :having args))

(defn merge-having
  "Merge a series of `having-clauses` together. Supports two optional args: a map to merge the results into, and a
  `conjunction` to use to combine clauses (defaults to `:and`).

    (merge-having [:= :x 1] [:= :y 2])
    {:having [:and [:= :x 1] [:= :y 2]]}

    (merge-having {:having [:= :x 1]} [:= :y 2])
    ;; -> {:having [:and [:= :x 1] [:= :y 2]]}

    (merge-having :or [:= :x 1] [:= :y 2])
    ;; -> {:having [:or [:= :x 1] [:= :y 2]]}"
  {:arglists '([& having-clauses]
               [m-or-conjunction & having-clauses]
               [m conjunction & having-clauses])}
  [& args]
  (merge-where-like :having args))

(defmethod build-clause :merge-having
  [_ m where-clause]
  (merge-having m where-clause))

(defhelper order-by [m fields]
  (assoc m :order-by (collify fields)))

(defhelper merge-order-by [m fields]
  (update-in m [:order-by] concat (collify fields)))

(defhelper limit [m l]
  (if (nil? l)
    m
    (assoc m :limit (if (coll? l) (first l) l))))

(defhelper offset [m o]
  (if (nil? o)
    m
    (assoc m :offset (if (coll? o) (first o) o))))

(defhelper lock [m lock]
  (cond-> m
    lock
    (assoc :lock lock)))

(defhelper modifiers [m ms]
  (if (nil? ms)
    m
    (assoc m :modifiers (collify ms))))

(defhelper merge-modifiers [m ms]
  (if (nil? ms)
    m
    (update-in m [:modifiers] concat (collify ms))))

(defmethod build-clause :insert-into [_ m table]
  (assoc m :insert-into table))

(defn insert-into
  ([table] (insert-into nil table))
  ([m table] (build-clause :insert-into m table)))

(defn- check-varargs
  "Called for helpers that require unrolled arguments to catch the mistake
  of passing a collection as a single argument."
  [helper args]
  (when (and (coll? args) (= 1 (count args)) (coll? (first args)))
    (let [msg (str (name helper) " takes varargs, not a single collection")]
      (throw #?(:clj (IllegalArgumentException. msg)
                :cljs (js/Error. msg))))))

(defmethod build-clause :columns [_ m fields]
  (assoc m :columns (collify fields)))

(defn columns [& args]
  (let [[m fields] (if (map? (first args))
                     [(first args) (rest args)]
                     [{} args])]
    (check-varargs :columns fields)
    (build-clause :columns m fields)))

(defmethod build-clause :merge-columns [_ m fields]
  (update-in m [:columns] concat (collify fields)))

(defn merge-columns [& args]
  (let [[m fields] (if (map? (first args))
                     [(first args) (rest args)]
                     [{} args])]
    (check-varargs :merge-columns fields)
    (build-clause :merge-columns m fields)))

(defhelper composite [m vs]
  (if (nil? vs)
    m
    (assoc m :composite (collify vs))))

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
  ([vs] (sset nil vs))
  ([m vs] (build-clause :set m vs)))

(defmethod build-clause :set0 [_ m values]
  (assoc m :set0 values))

;; set with lower priority (before from)
(defn set0
  ([vs] (set0 nil vs))
  ([m vs] (build-clause :set0 m vs)))

(defmethod build-clause :set [_ m values]
  (assoc m :set values))

;; set with higher priority (after join)
(defn set1
  ([vs] (set1 nil vs))
  ([m vs] (build-clause :set1 m vs)))

(defmethod build-clause :delete-from [_ m table]
  (assoc m :delete-from table))

(defn delete-from
  ([table] (delete-from nil table))
  ([m table] (build-clause :delete-from m table)))

(defmethod build-clause :delete [_ m tables]
  (assoc m :delete tables))

(defn delete
  ([tables] (delete nil tables))
  ([m tables] (build-clause :delete m tables)))

(defmethod build-clause :truncate [_ m table]
  (assoc m :truncate table))

(defn truncate
  ([table] (truncate nil table))
  ([m table] (build-clause :truncate m table)))

(defhelper with [m ctes]
  (assoc m :with ctes))

(defhelper with-recursive [m ctes]
  (assoc m :with-recursive ctes))

(defmethod build-clause :union [_ m maps]
  (assoc m :union maps))

(defmethod build-clause :union-all [_ m maps]
  (assoc m :union-all maps))

(defmethod build-clause :intersect [_ m maps]
  (assoc m :intersect maps))

(defmethod build-clause :except [_ m maps]
  (assoc m :except maps))
