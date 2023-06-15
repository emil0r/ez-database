(ns dev
  (:require [cheshire.core :as json]
            [ez-database.core :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as jdbc.rs]
            [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter])
  (:import [java.sql Array PreparedStatement]
           [org.postgresql.util PGobject]))


(defn <-pgobject
  [data]
  (json/parse-string (.getValue data) true))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (json/generate-string x)))))

(extend-protocol jdbc.rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v))
  Array
  (read-column-by-label [^Array v _]    (vec (.getArray v)))
  (read-column-by-index [^Array v _ _]  (vec (.getArray v))))

(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

(def db-spec-1 ;; {:classname "org.postgresql.Driver"
               ;;  :subprotocol "postgresql"
               ;;  :subname "//localhost:5432/ezdb1"
               ;;  :user "postgres"
  ;;  :password "postgres"}
  {:dbtype "postgres"
   :dbname "ezdb1"
   :user "postgres"
   :port 8432
   :password "postgres"}
  )

(def db-spec-2 {:classname "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname "//localhost:5432/ezdb2"
                :user "postgres"
                :port 8432
                :password "postgres"})



(let [db (db/map->EzDatabase {:db-specs {:default db-spec-1
                                         :extra db-spec-2}
                              :ezdb-opts {:builder-fn jdbc.rs/as-unqualified-maps}})]
  (db/query! db {:delete-from :test})
  (db/query! db ["insert into test values (?), (?);" (rand-int 500) (rand-int 500)])
  (db/query db ["select * from test"])
  ;; (db/query! db {:insert-into :test
  ;;                :values [{:id (rand-int 10000)}
  ;;                         {:id (rand-int 10000)}
  ;;                         {:id (rand-int 10000)}]})
  ;; (db/query<! db {:insert-into :test
  ;;                 :values (into [[:id]]
  ;;                               (map (fn [_] [(rand-int 1000)]) (range 5)))}
  ;;             {:batch true})
  ;; (db/query db {:select [:*]
  ;;               :from [:test]})
  )

(let [db (db/map->EzDatabase {:db-specs {:default db-spec-1
                                         :extra db-spec-2}})]
  ;; (db/query! db {:delete-from :testus})
  ;; (db/query! db {:insert-into :testus
  ;;                :values [{:data [:lift {:foobar (rand-int 100)}]}]})
  ;; (db/query db {:select [:*]
  ;;               :from [:testus]})
  (db/with-transaction [db :default]

    (println (db/query db {:select [:*]
                           :from [:testus]}))
    (db/query! db {:insert-into :testus
                   :values [{:data [:lift {:mooo (rand-int 100)}]}]})
    (println (db/query db {:select [:*]
                           :from [:testus]}))
    (db/query db {:select [:*]
                  :from :does-not-exist})))


(hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc {:builder-fn jdbc.rs/as-kebab-maps}))

(hugsql/def-db-fns "queries/test.sql")

(let [db (db/map->EzDatabase {:db-specs {:default db-spec-1
                                         :extra db-spec-2}})
      spec (get-in db [:db-specs :default])]
  (db/query db sql-fn-query)
  ;;(db/query! db sql-fn-insert! {:id 183})
  (db/query<! db sql-fn-insert<! {:id 183})
  (db/query! db sql-fn-delete! {:id 183})
  #_(sql-fn-query spec spec)
  ;;(sql-fn-insert! spec {:id 183})
  )
