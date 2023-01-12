(ns ez-database.test.query
  (:require [clojure.zip :as zip]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers]
            [ez-database.query :refer [optional clean swap]]
            [ez-database.query.zipper :as query.zipper]
            [midje.sweet :refer :all]))



(fact
 "clean HoneySQL query map with query/clean using query.zipper/zipper in the background"
 (clean
  (-> {:select [:*]
       :from [:test]}
      (swap true (sql.helpers/where [:and
                                     [:= :foo true]
                                     (optional false [:= :foo false])]))))
 => {:select [:*]
     :from [:test]
     :where [:and [:= :foo true]]})

(fact
 "optional macro"
 (clean
  {:with [[:images {:select [:*]
                    :from [:images]}]]
   :select [:*]
   :from [:test]
   :join [(optional (not (nil? 1))
                    [:images :i] [:= :test.id :i.id])]})
 => {:with [[:images {:select [:*]
                      :from [:images]}]]
     :select [:*]
     :from [:test]
     :join [[:images :i] [:= :test.id :i.id]]})
