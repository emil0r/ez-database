(ns ez-database.test.queries
  (:require [clojure.java.jdbc :as jdbc]
            [ez-database.core :as db]
            [ez-database.test.core :refer [reset-db! db-spec db-spec-2]]
            [midje.sweet :refer :all]
            [yesql.core :refer [defqueries]]))

(defqueries "queries/test.sql")


(fact "queries"
 (let [db (db/map->EzDatabase {:db-specs {:default db-spec
                                          :extra db-spec-2}})
       reset-db? true]

   (fact "nil"
         (try (db/query db nil)
              (catch Exception e
                (.getMessage (:exception (ex-data e)))))

         => "nil can't be run as a query")

   (fact "post-query"
         (when reset-db?
           (reset-db!))
         (fact "remove-ks :id"
               (->> "select id from test order by id;"
                    (db/query db ^:opts {[:remove-ks :post] #{:id}})
                    (map :id))
               => [nil nil])
         (fact "remove :id"
               (->> "select id from test order by id;"
                    (db/query db ^:opts {[:remove :post] (fn [[k v]] (integer? v))})
                    (map :id))
               => [nil nil])
         (fact))

   (fact "string"
         (when reset-db?
           (reset-db!))
         (fact "string"
               (->> "select id from test order by id;"
                    (db/query db)
                    (map :id)) => [0 42])
         (fact "string with args"
               (->> (db/query db "select id from test where id > ? order by id;" [0])
                    (map :id)) => [42])
         (fact "insert with scalar return value"
               (let [r (db/query! db "insert into test values (-1);")
                     ids (->> sql-fn-query
                              (db/query db)
                              (map :id))]
                 [r ids]) => [[1] [-1 0 42]])
         (fact "insert with return value"
               (db/query<! db "test" [{:id -3}])
               => [{:id -3}])
         (fact "delete"
               (let [r (db/query! db "delete from test where id = -1;")
                     ids (->> sql-fn-query
                              (db/query db)
                              (map :id))]
                 [r ids] => [[1] [-3 0 42]])))

   (fact "sequential"
         (when reset-db?
           (reset-db!))
         (fact "select"
               (->> ["select id from test order by id;"]
                    (db/query db)
                    (map :id)) => [0 42])
         (fact "select with args in first sequential"
               (->> (list "select id from test where id > ? and id < ? order by id;" 0 30)
                    (db/query db)
                    (map :id)) => [])
         (fact "select with lazyseq"
               (->> (map identity ["select id from test where id > ? and id < ? order by id;" 0 50])
                    (db/query db)
                    (map :id)) => [42])
         (fact "select with args"
               (->> (db/query db (list "select id from test where id > ? and id < ? order by id;") [0 50])
                    (map :id)) => [42])
         (fact "insert with return scalar value"
               (let [r (db/query! db ["insert into test values (?);" -4])
                     ids (->> sql-fn-query
                              (db/query db)
                              (map :id))]
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
                    (db/query db)
                    (map :id)) => [0 42])
         (fact "insert with scalar return value"
               (let [r (db/query! db sql-fn-insert! {:id -2})
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
   (fact "hashmap"
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

   (fact "extra db"
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
   (fact "database"
         (db/databases db) => [:default :extra])

   (fact "transaction"
         (try
           (db/with-transaction [db]
             (db/query! db {:insert-into :test :values [{:id 43}]})
             (db/query! db {:insert-into :test :values [{:id 44}]})
             (db/query! db {:insert-into :test :values [{:id "asdf"}]}))
           (catch Exception e
             (->> (db/query db {:select [:*]
                                :from [:test]})
                  (map :id))))
         => [0 42])

   (fact "params with honeysql"
         (when reset-db?
           (reset-db!))

         (db/query db {:select [:*] :from [:test] :where [:= :id #sql/param :id]} {:id 0})
         => [{:id 0}])

   (fact "registering"
         (when reset-db?
           (reset-db!))
         (db/clear-queries!)
         (fact "honeysql"
               (db/register-query! :query/testus1 [nil nil {:select [:*]
                                                            :from [:test]
                                                            :where [:> :id #sql/param :id]}])
               
               (->> {:id 1}
                    (db/query db :query/testus1)
                    (map :id))
               => [42])
         (fact "string"
               (db/register-query! :query/testus2 [nil nil "select * from test where id > ?"])
               (->> [1]
                    (db/query db :query/testus2)
                    (map :id))
               => [42]))))

(fact "error reporting"
      (let [db (db/map->EzDatabase {:db-specs {:default db-spec
                                               :extra db-spec-2}})
            reset-db? true]
        (fact "batch inserts"
              (when reset-db?
                (reset-db!))
              (try
               (db/query! db {:insert-into :test :values [{:id 3}
                                                          {:id 4}
                                                          {:id "5" :foobar "Asdf"}]})
               (catch Exception e
                 (count (:messages (ex-data e))))) => 2)))
