(ns ez-database.builder
  (:require [ez-database.query :as query]
            [honeysql.helpers :as sql.helpers]))

(defn- build-equality [constraint data]
  (let [[eq col k] (if (= (count constraint) 3)
                       constraint
                       (into [:=] constraint))
        [k col] (cond (nil? k)
                      [col col]
                      (nil? col)
                      [k k]
                      :else
                      [k col])
        d (get data k)]
    (if d
      [eq col (get data k)]
      nil)))

(defn- build-where [constraints data]
  (reduce (fn [out constraint]
            (if-let [eq (build-equality constraint data)]
              (conj out eq)
              out))
          [:and] constraints))

(defn- get-base-query [{:keys [operation table values value select order-by] :or {select [:*]}}]
  (case operation
    :delete {:delete-from table}
    :insert {:insert-into table :values values}
    :update {:update table :set value}
    :select (merge {:select select :from [table]}
                   (if order-by
                     {:order-by order-by}))
    (throw (ex-info "Unknown operation" {:operation operation}))))

(defn- select? [composition]
  (= :select (:operation composition)))
(defn- insert? [composition]
  (= :insert (:operation composition)))

(defn simple-query-builder [composition]
  (let [{:keys [constraints]} composition]
    (assert (set? constraints) "constraints needs to be a set of tuples of keywords")
    (fn [data]
      (let [base-query (get-base-query (merge composition data))
            where-constraints (if (not (insert? composition))
                                (build-where constraints data))]
        (-> base-query
            (query/swap (not= [:and] where-constraints)
                        (sql.helpers/where where-constraints))
            (query/swap (and (contains? data :limit)
                             (select? composition))
                        (sql.helpers/limit (get data :limit)))
            (query/swap (and (contains? data :offset)
                             (select? composition))
                        (sql.helpers/offset (get data :offset)))
            (query/swap (and (contains? data :order-by)
                             (select? composition))
                        ((fn [q]
                           (apply sql.helpers/order-by q (get data :order-by)))))
            (query/clean))))))

(defmacro defsimplequery [query-name composition]
  `(def ~query-name (simple-query-builder ~composition)))
