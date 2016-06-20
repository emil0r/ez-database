(ns ez-database.core
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as honeysql]
            [slingshot.slingshot :refer [try+ throw+]]))


(defprotocol IEzDatabase
  (query [database query] [database key? query] [database key query args])
  (query! [database query] [database key? query] [database key query args])
  (query<! [database query] [database key? query] [database key query args])
  (databases [database]))

(defprotocol IEzQuery
  (run-query [query database] [query database args])
  (run-query! [query database] [query database args])
  (run-query<! [query database] [query database args]))

(def ^:dynamic *connection* nil)

(defn get-connection [db-specs key]
  (if-not (nil? *connection*)
    *connection*
    (get db-specs key)))

(defmacro with-transaction [binding & body]
  `(jdbc/db-transaction* (get-connection (:db-specs (first ~binding)) (second ~binding))
                         (^{:once true} fn* [~'con]
                          (binding [*connection* ~'con]
                            ~@body))
                         ~@(rest (rest binding))))

(defn- get-causes-messages [e]
  (loop [msg []
         e e
         breakpoint 0]
    (if (or (nil? e)
            (> breakpoint 100))
      msg
      (recur (try
               (conj msg (.getMessage e))
               (catch Exception _
                 msg))
             (try
               (.getNextException e)
               (catch Exception _
                 nil))
             (inc breakpoint)))))

(defmacro try-query [& body]
  `(try+
    ~@body
    (catch Object ~'e
      (throw+ {:type ::try-query
               :exception ~'e
               :messages (get-causes-messages ~'e)
               :query ~'query}))))

(defmacro try-query-args [& body]
  `(try+
    ~@body
    (catch Object ~'e
      (throw+ {:type ::try-query-args
               :exception ~'e
               :messages (get-causes-messages ~'e)
               :query ~'query
               :args ~'args}))))

(defn throw-msg [msg]
  (throw+ {:type ::error-msg
           :msg msg}))

(extend-protocol IEzQuery

  nil
  (run-query
    ([query db]
       (throw-msg "nil can't be run as a query"))
    ([query db args]
       (throw-msg "nil can't be run as a query")))
  (run-query!
    ([query db]
       (throw-msg "nil can't be run as a query!"))
    ([query db args]
       (throw-msg "nil can't be run as a query!")))
  (run-query<!
    ([query db]
       (throw-msg "nil can't be run as a query<!"))
    ([query db args]
       (throw-msg "nil can't be run as a query<!")))

  java.lang.String
  (run-query
    ([query db]
       (jdbc/query db [query]))
    ([query db args]
       (jdbc/query db (concat [query] args))))
  (run-query!
    ([query db]
       (jdbc/execute! db [query]))
    ([query db args]
       (jdbc/execute! db (concat [query] args))))
  (run-query<!
    ([query db]
       (throw-msg "String without args is not allowed for query<!"))
    ([query db args]
       (apply jdbc/insert! db query args)))

  clojure.lang.Sequential
  (run-query
    ([query db]
       (jdbc/query db query))
    ([query db args]
       (jdbc/query db (concat query args))))
  (run-query!
    ([query db]
       (jdbc/execute! db query))
    ([query db args]
       (jdbc/execute! db (concat query args))))
  (run-query<!
    ([query db]
       (throw-msg "Sequence is not allowed for query<!"))
    ([query db args]
       (throw-msg "Sequence is not allowed for query<!")))

  clojure.lang.PersistentArrayMap
  (run-query
    ([query db]
       (jdbc/query db (honeysql/format query)))
    ([query db args]
       (jdbc/query db (honeysql/format query args))))
  (run-query!
    ([query db]
       (jdbc/execute! db (honeysql/format query)))
    ([query db args]
       (jdbc/execute! db (honeysql/format query args))))
  (run-query<!
    ([query db]
       (let [table (:insert-into query)
             values (:values query)]
         (apply jdbc/insert! db table values)))
    ([query db args]
       (let [table (:insert-into query)
             values (:values query)]
         (apply jdbc/insert! db table values))))

  clojure.lang.PersistentHashMap
  (run-query
    ([query db]
       (jdbc/query db (honeysql/format query)))
    ([query db args]
       (jdbc/query db (honeysql/format query args))))
  (run-query!
    ([query db]
       (jdbc/execute! db (honeysql/format query)))
    ([query db args]
       (jdbc/execute! db (honeysql/format query args))))
  (run-query<!
    ([query db]
       (let [table (:insert-into query)
             values (:values query)]
         (apply jdbc/insert! db table values)))
    ([query db args]
       (let [table (:insert-into query)
             values (:values query)]
         (apply jdbc/insert! db table values))))

  clojure.lang.Fn
  (run-query
    ([query db]
       (query {} {:connection db}))
    ([query db args]
       (query args {:connection db})))
  (run-query!
    ([query db]
       (query {} {:connection db}))
    ([query db args]
       (query args {:connection db})))
  (run-query<!
    ([query db]
       (query {} {:connection db}))
    ([query db args]
       (query args {:connection db}))))


(defrecord EzDatabase [db-specs ds-specs]
  IEzDatabase

  (query [db query]
    (try-query
     (run-query query (get-connection db-specs :default))))

  (query [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query-args
       (if (nil? args)
         (run-query query (get-connection db-specs key))
         (run-query query (get-connection db-specs key) args)))))

  (query [db key query args]
    (try-query-args
     (run-query query (get-connection db-specs key) args)))

  (query! [db query]
    (try-query
     (run-query! query (get-connection db-specs :default))))

  (query! [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query
       (if (nil? args)
         (run-query! query (get-connection db-specs key))
         (run-query! query (get-connection db-specs key) args)))))

  (query! [db key query args]
    (try-query-args
     (run-query! query (get-connection db-specs key) args)))

  (query<! [db query]
    (try-query
     (run-query<! query (get-connection db-specs :default))))
  (query<! [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query-args
       (if (nil? args)
         (run-query<! query (get-connection db-specs key))
         (run-query<! query (get-connection db-specs key) args)))))
  (query<! [db key query args]
    (try-query-args
     (run-query<! query (get-connection db-specs key) args)))

  (databases [db]
    (keys db-specs)))
