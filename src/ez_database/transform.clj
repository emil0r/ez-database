(ns ez-database.transform
  "Handle transformations from and to the database"
  (:refer-clojure :exclude [remove])
  (:require [clojure.spec.alpha :as s]))

(def -t (atom {}))

(def ->transform nil)
(defmulti ->transform (fn [k m old-data new-data]
                        (if (fn? (get m k))
                          :fn
                          :keyword)))
(defmethod ->transform :fn [k m old-data new-data]
  ((get m k) old-data new-data))
(defmethod ->transform :keyword [k m old-data new-data]
  (assoc new-data (get m k) (get old-data k)))
(defmethod ->transform :default [k m _ new-data]
  ;; Do nothing. We could use a throw here, or log the mistake,
  ;; but ultimately it's probably better to let the deveoper
  ;; decide for the themselves how they want to handle this
  new-data)

(defn- add-args [args reverse?]
  (->> args
       (map #(into [] (take 2 (if reverse?
                                (reverse %)
                                %))))
       (into {})))

(defn add
  "Add a transformation pair"
  [a b & args]
  (do (swap! -t merge {[a b] (add-args args false)
                       [b a] (add-args args true)})
      nil))
(defn remove
  "Remove a pairing"
  [a b]
  (do (swap! -t dissoc [a b] [b a])
      nil))

(defn transform
  "Transform a map of data into another map of data according to the specified transformations"
  ([a b data] (transform nil a b data))
  ([opts a b data]
   (let [transformations (get @-t [a b])]
     (when (nil? transformations)
       (throw (ex-info "Invalid transformation wanted" {:from a
                                                        :to b
                                                        :opts opts})))
     (let [new-data (reduce (fn [out k]
                              (->transform k transformations data out))
                            {} (keys transformations))]
       (when-let [spec (:validate opts)]
         (when-not (s/valid? spec new-data)
           (throw (ex-info "Invalid spec" (s/explain-data spec new-data)))))
       (if (true? (:nil? opts))
         new-data
         (->> new-data
              (clojure.core/remove (fn [[k v]]
                                     (nil? v)))
              (into {})))))))
