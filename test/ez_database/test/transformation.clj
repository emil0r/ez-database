(ns ez-database.test.transformation
  (:require [clojure.java.jdbc :as jdbc]
            [ez-database.core :as db]
            [ez-database.transform :as transform]
            [ez-database.test.core :refer [reset-db! db-spec db-spec-2]]
            [midje.sweet :refer :all]
            [yesql.core :refer [defqueries]]))


(defqueries "queries/test.sql")


(fact "queries"
      (let [db (db/map->EzDatabase {:db-specs {:default db-spec
                                               :extra db-spec-2}})
            reset-db? true]

        ;; add transformations

        (transform/add :foo :foo/bar
                       [:id ::id])

        (fact "string"
              (when reset-db?
                (reset-db!))
              (fact "string"
                    (->> "select id from test order by id;"
                         (db/query db ^:opts {:transformation [:foo :foo/bar]})
                         (map ::id)) => [0 42])
              (fact "string with args"
                    (->> (db/query db
                                   ^:opts {:transformation [:foo :foo/bar]}
                                   "select id from test where id > ? order by id;" [0])
                         (map ::id)) => [42])
              (fact "insert with scalar return value"
                    (let [r (db/query! db "insert into test values (-1);")
                          ids (->> sql-fn-query
                                   (db/query db ^:opts {:transformation [:foo :foo/bar]})
                                   (map ::id))]
                      [r ids]) => [[1] [-1 0 42]])
              (fact "insert with return value"
                    (db/query<! db
                                ^:opts {:transformation [:foo/bar :foo]}
                                "test"
                                [{::id -3}])
                    => [{:id -3}])
              (fact "delete"
                    (let [r (db/query! db "delete from test where id = -1;")
                          ids (->> sql-fn-query
                                   (db/query db ^:opts {:transformation [:foo :foo/bar]})
                                   (map ::id))]
                      [r ids] => [[1] [-3 0 42]])))

        (fact "sequential"
              (when reset-db?
                (reset-db!))
              (fact "select"
                    (->> ["select id from test order by id;"]
                         (db/query db ^:opts {:transformation [:foo :foo/bar]})
                         (map ::id)) => [0 42])
              (fact "select with args in first sequential"
                    (->> (list "select id from test where id > ? and id < ? order by id;" 0 30)
                         (db/query db ^:opts {:transformation [:foo :foo/bar]})
                         (map ::id)) => [])
              (fact "select with lazyseq"
                    (->> (map identity ["select id from test where id > ? and id < ? order by id;" 0 50])
                         (db/query db ^:opts {:transformation [:foo :foo/bar]})
                         (map ::id)) => [42])
              (fact "select with args"
                    (->> (db/query db
                                   ^:opts {:transformation [:foo :foo/bar]}
                                   (list "select id from test where id > ? and id < ? order by id;") [0 50])
                         (map ::id)) => [42])
              (fact "insert with return scalar value"
                    (let [r (db/query! db ["insert into test values (?);" -4])
                          ids (->> sql-fn-query
                                   (db/query db ^:opts {:transformation [:foo :foo/bar]})
                                   (map ::id))]
                      [r ids]) => [[1] [-4 0 42]])
              (fact "delete"
                    (let [r (db/query! db ["delete from test where id = ?;" -4])
                          ids (->> sql-fn-query
                                   (db/query db)
                                   (map :id))]
                      [r ids] => [[1] [0 42]])))
        (fact "function"
              (when reset-db?
                (reset-db!))
              (fact "select"
                    (->> sql-fn-query
                         (db/query db ^:opts {:transformation [:foo :foo/bar]})
                         (map ::id)) => [0 42])
              (fact "insert with scalar return value"
                    (let [r (db/query! db
                                       ^:opts {:transformation [:foo/bar :foo]}
                                       sql-fn-insert!
                                       {::id -2})
                          ids (->> sql-fn-query
                                   (db/query db)
                                   (map :id))]
                      [r ids]) => [1 [-2 0 42]])
              (fact "insert with return value"
                    (db/query<! db sql-fn-insert<! {:id -1})
                    => {:id -1})
              (fact "delete"
                    (let [r (db/query! db sql-fn-delete! {:id -2})
                          ids (->> sql-fn-query
                                   (db/query db)
                                   (map :id))]
                      [r ids] => [1 [-1 0 42]])))
        #_(fact "hashmap"
              (when reset-db?
                (reset-db!))
              (fact "select"
                    (->> {:select [:id]
                          :from [:test]
                          :order-by [:id]}
                         (db/query db)
                         (map :id)) => [0 42])
              (fact "insert with scalar return value"
                    (let [r (db/query! db {:insert-into :test
                                           :values [{:id -3}]})
                          ids (->> sql-fn-query
                                   (db/query db)
                                   (map :id))]
                      [r ids]) => [[1] [-3 0 42]])
              (fact "insert with return value"
                    (db/query<! db {:insert-into :test
                                    :values [{:id -2}]})
                    => [{:id -2}])
              (fact "delete"
                    (let [r (db/query! db {:delete-from :test
                                           :where [:= :id -3]})
                          ids (->> sql-fn-query
                                   (db/query db)
                                   (map :id))]
                      [r ids] => [[1] [-2 0 42]])))

        #_(fact "extra db"
              (when reset-db?
                (reset-db!))
              (fact "insert with string"
                    (db/query! db :extra "insert into test values (?), (?);" [0 42]))
              (fact "select with string"
                    (->> "select id from test order by id;"
                         (db/query db :extra)
                         (map :id)) => [0 42])
              (fact "select with vector"
                    (->> ["select id from test order by id;"]
                         (db/query db :extra)
                         (map :id)) => [0 42])
              (fact "select with list, 0 < id < 30"
                    (->> (list "select id from test where id > ? and id < ? order by id;" 0 30)
                         (db/query db :extra)
                         (map :id)) => [])

              (fact "select with fn"
                    (->> sql-fn-query
                         (db/query db :extra)
                         (map :id)) => [0 42])
              (fact "select with hashmap"
                    (->> {:select [:id]
                          :from [:test]
                          :order-by [:id]}
                         (db/query db :extra)
                         (map :id)) => [0 42]))

        #_(fact "transaction"
              (try
                (db/with-transaction [db]
                  (db/query! db {:insert-into :test :values [{:id 43}]})
                  (db/query! db {:insert-into :test :values [{:id 44}]})
                  (db/query! db {:insert-into :test :values [{:id "asdf"}]}))
                (catch Exception e
                  (->> (db/query db {:select [:*]
                                     :from [:test]})
                       (map :id))))
              => [0 42])))
