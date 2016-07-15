(ns ez-database.query
  "Namespace for manipulating HoneySQL queries"
  (:require [clojure.zip :as zip]
            [ez-database.query.zipper :refer [zipper]]))


(defmacro optional
  "Optional query arguments"
  ([pred? & r]
   `(if ~pred?
      [:##holder## ~@r]
      :##nil##)))

(defmacro swap
  "Query, predicate and the HoneySQL helper function"
  ([q pred? helper]
   `(if ~pred?
      (-> ~q ~helper)
      ~q)))

(defn clean
  "Clean a query map from optional :##nil## values"
  [query]
  (loop [loc (zipper query)]
    (let [next-loc (zip/next loc)]
      (if (zip/end? next-loc)
        (zip/root loc)
        (cond
          (= :##nil## (zip/node next-loc))
          (recur (zip/remove next-loc))

          (= :##holder## (zip/node next-loc))
          (recur (-> (reduce (fn [out loc]
                               ;; insert to the left of out.
                               ;; this is because they are inserted in
                               ;; reverse order to what we want it
                               ;; to be when in the map
                               (zip/insert-left out loc))
                             ;; we move up one step for out
                             (-> next-loc zip/up)
                             ;; everything right of next-loc
                             (zip/rights next-loc))
                     ;; remove the node that holds
                     ;; the :##holder## keyword
                     zip/remove))

          :else
          (recur next-loc))))))
