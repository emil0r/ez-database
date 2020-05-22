(ns ez-database.test.core
  (:require [clojure.java.jdbc :as jdbc]
            [ez-database.core :as db]
            [joplin.core :as joplin]
            [joplin.jdbc.database]))



(defn run [target & args]
  (let [db-spec (-> target :db :db-spec)]
    (jdbc/insert-multi! db-spec "test" [:id] [[0] [42]])))

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname "//localhost:5432/ezdb1"
              :user "postgres"
              :password "postgres"})

(def db-spec-2 {:classname "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname "//localhost:5432/ezdb2"
                :user "postgres"
                :password "postgres"})


(def mmap {:db {:type :sql
                :url "jdbc:postgresql://localhost:5432/ezdb1?user=postgres&password=postgres"
                :db-spec db-spec}
           :migrator "resources/ezdb/migrations/postgresql/"
           :seed "ez-database.test.core/run"})

(def mmap-2 {:db {:type :sql
                  :url "jdbc:postgresql://localhost:5432/ezdb2?user=postgres&password=postgres"
                  :db-spec db-spec}
             :migrator "resources/ezdb/migrations/postgresql/"})

(defn reset-db! []
  (with-out-str
    (joplin/rollback-db mmap 9999)
    (joplin/migrate-db mmap)
    (joplin/seed-db mmap)

    (joplin/rollback-db mmap-2 9999)
    (joplin/migrate-db mmap-2)))
