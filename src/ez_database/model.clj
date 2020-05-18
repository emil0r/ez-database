(ns ez-database.model
  (:refer-clojure :exclude [update])
  (:require [ez-database.builder :refer [simple-query-builder]]
            [ez-database.core :as db]
            [ez-database.transform :as transform]))

(defprotocol IModel
  (delete [model db constraints])
  (update [model db constraints value])
  (insert [model db values])
  (select [model db constraints]))


(defn- run-transformation-on-values [opts transformation values]
  (if transformation
    (let [[a b] transformation]
      (transform/transform opts b a values))
    values))

(defrecord Model [model-name table transformation-opts transformation operations db-key]
  IModel
  (select [this db constraints]
    (let [operation (get operations :select)]
      (let [query-fn (get-in operations [:select :query-fn])
            query (query-fn (assoc constraints
                                   :operation :select))
            opts (get-in operations [:select :opts])]
        (db/query db db-key opts query))))
  (delete [this db constraints]
    (let [operation (get operations :delete)]
      (let [query-fn (get-in operations [:delete :query-fn])
            query (query-fn (assoc constraints
                                   :operation :delete))]
        (db/query! db db-key query))))
  (update [this db constraints value]
    (let [operation (get operations :update)]
      (do (assert (map? value) "value need to be a map")
          (let [query-fn (get-in operations [:update :query-fn])
                transformation-opts (get-in operations [operation :transformation-opts] transformation-opts)
                query (query-fn (assoc constraints
                                       :operation :update
                                       :value (run-transformation-on-values transformation-opts transformation value)))
                opts (get-in operations [:update :opts])]
            (db/query! db opts db-key query)))))
  (insert [this db values]
    (let [operation (get operations :insert)]
      (do (assert (vector? values) "values need to be a vector of maps")
          (let [query-fn (get-in operations [:insert :query-fn])
                transformation-opts (get-in operations [operation :transformation-opts] transformation-opts)
                query (query-fn {:operation :insert
                                 :values (map #(run-transformation-on-values transformation-opts transformation %) values)})
                opts (get-in operations [:insert :opts])]
            (db/query<! db opts db-key query))))))

(defn- get-opts [transformation operation]
  (let [[a b] transformation]
   (cond
     (and (= operation :select) transformation)
     ^:opts {[:transformation :post]
             [a b]}
     (= operation :delete)
     nil

     (and (= operation :update) transformation)
     ^:opts {[:transformation :post]
             [a b]}

     (and (= operation :insert) transformation)
     ^:opts {[:transformation :post]
             [a b]}

     :else
     nil)))

(defn model [{:keys [model transformation transformation-opts model-name table] :as data}]
  (assert (some? (:table model)) "table needs to be set")
  (let [operations (->> #{:insert :update :delete :select}
                        (reduce (fn [out operation]
                                  (let [model (get-in data [operation :model] model)
                                        opts (get-opts transformation operation)]
                                    (assoc out operation {:query-fn (simple-query-builder (assoc model :operation operation))
                                                          :opts opts
                                                          :transformation-opts (get-in data [operation :transformation-opts])})))
                                {}))
        db-key (get data :db-key :default)]
    (when transformation
      (apply transform/add transformation))
    (map->Model {:operations operations
                 :db-key db-key
                 :transformation-opts (or transformation-opts {:nil false})
                 :transformation transformation
                 :table (:table model)
                 :model-name model-name})))

(defmacro defmodel [model-name opts]
  (let [sym-model-name (str model-name)]
    `(def ~model-name (model (assoc ~opts :model-name ~sym-model-name)))))
