(ns ez-database.test.core
  (:require [clojure.java.jdbc :as jdbc]
            [joplin.core :as joplin]
            joplin.jdbc.database))



(defn run [target & args]
  (let [db-spec (-> target :db :db-spec)]
    (jdbc/insert! db-spec "test" [:id] [0] [42])))

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname "//localhost:5432/dev_ez_database"
              :user "devuser"
              :password "devuser"})

(def db-spec-2 {:classname "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname "//localhost:5432/dev_ez_database_2"
                :user "devuser"
                :password "devuser"})


(def mmap {:db {:type :sql
                :url "jdbc:postgresql://localhost:5432/dev_ez_database?user=devuser&password=devuser"
                :db-spec db-spec}
           :migrator "resources/migrations/postgresql/"
           :seed "ez-database.test.core/run"})

(def mmap-2 {:db {:type :sql
                  :url "jdbc:postgresql://localhost:5432/dev_ez_database_2?user=devuser&password=devuser"
                  :db-spec db-spec}
             :migrator "resources/migrations/postgresql/"})

(defn reset-db! []
  (joplin/rollback-db mmap 9999)
  (joplin/migrate-db mmap)
  (joplin/seed-db mmap)

  (joplin/rollback-db mmap-2 9999)
  (joplin/migrate-db mmap-2))
