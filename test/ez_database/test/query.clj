(ns ez-database.test.query
  (:require [clojure.zip :as zip]
            [honeysql.core :as sql]
            [honeysql.helpers :as sql.helpers]
            [ez-database.query :refer [optional clean]]
            [ez-database.query.zipper :as query.zipper]
            [midje.sweet :refer :all]))



(fact
 "clean HoneySQL query map with query/clean using query.zipper/zipper in the background"
 (clean
  (-> {:select [:*]
       :from [:test]}
      (optional true (sql.helpers/where [:and
                                         [:= :foo true]
                                         (optional false [:= :foo false])]))))
 => {:select [:*]
     :from [:test]
     :where [:and [:= :foo true]]})
