(ns ez-database.test.model
  (:require [ez-database.core :as db]
            [ez-database.model :as model :refer [defmodel]]
            [ez-database.test.core :refer [reset-db! db-spec db-spec-2]]
            [midje.sweet :refer :all]))


(defmodel TestModel {:model {:table :test
                             :constraints #{[:= :id :test/id]}}
                     :transformation [:test :test/test
                                      [:id :test/id]]})

(fact "model"
      (let [db (db/map->EzDatabase {:db-specs {:default db-spec
                                               :extra db-spec-2}})
            reset-db? true]
        (when reset-db?
          (reset-db!))
        (fact "select all"
              (model/select TestModel db nil) => [{:test/id 0} {:test/id 42}])
        (fact "select id 0"
              (model/select TestModel db {:test/id 0}) => [{:test/id 0}])
        (fact "delete id 42"
              (model/delete TestModel db {:test/id 42}) => [1])
        (fact "select id 42 after deleting id 42"
              (model/select TestModel db {:test/id 42}) => [])
        (fact "insert id -1 and -2"
              (model/insert TestModel db [{:test/id -1} {:test/id -2}])
              => [{:test/id -1} {:test/id -2}])
        (fact "update id"
              (model/update TestModel db {:test/id -1} {:test/id 101})
              => [1])))
