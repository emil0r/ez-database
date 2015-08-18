(ns ez-database.core
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as honeysql]
            [slingshot.slingshot :refer [try+ throw+]]))


(defprotocol IEzDatabase
  (query [database query] [database key? query] [database key query args])
  (query! [database query] [database key? query] [database key query args])
  (query<! [database query] [database key? query] [database key query args])
  (databases [database]))


(def ^:dynamic *connection* nil)

(defn- get-connection [db-specs key]
  (if-not (nil? *connection*)
    (assoc (get db-specs key) :connection *connection*)
    (get db-specs key)))

(defmacro try-query [& body]
  `(try+
    ~@body
    (catch Object ~'e
      (throw+ {:type ::try-query
               :exception ~'e
               :query ~'query}))))

(defmacro try-query-args [& body]
  `(try+
    ~@body
    (catch Object ~'e
      (throw+ {:type ::try-query-args
               :exception ~'e
               :query ~'query
               :args ~'args}))))

(defn throw-msg [msg]
  (throw+ {:type ::error-msg
           :msg msg}))

(defrecord EzDatabase [db-specs ds-specs]
  IEzDatabase

  (query [db query]
    (try-query
     (cond
      (string? query)
      (jdbc/query (get-connection db-specs :default) [query])

      (sequential? query)
      (jdbc/query (get-connection db-specs :default) query)

      (fn? query)
      (query {} {:connection (get-connection db-specs :default)})

      :else
      (jdbc/query (get-connection db-specs :default) (honeysql/format query)))))

  (query [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query-args
       (cond
        ;; if args nil and query is fn, send in empty hashmap as args
        (and (nil? args) (fn? query))
        (query {} {:connection (get-connection db-specs key)})

        ;; otherwise use the args
        (fn? query)
        (query args {:connection (get-connection db-specs key)})

        (and (string? query)
             (nil? args))
        (jdbc/query (get-connection db-specs key) [query])

        (and (sequential? query)
             (nil? args))
        (jdbc/query (get-connection db-specs key) query)

        (sequential? query)
        (jdbc/query (get-connection db-specs key) (concat query args))

        (string? query)
        (jdbc/query (get-connection db-specs key) (concat [query] args))

        (nil? args)
        (jdbc/query (get-connection db-specs key) (honeysql/format query))

        :else
        (jdbc/query (get-connection db-specs key) (honeysql/format query args))))))

  (query [db key query args]
    (try-query-args
     (cond
      (fn? query) (query args {:connection (get-connection db-specs key)})

      (string? query)
      (jdbc/query (get-connection db-specs key) (concat [query] args))

      (sequential? query)
      (jdbc/query (get-connection db-specs key) (concat query args))

      :else (jdbc/query (get-connection db-specs key) (honeysql/format query args)))))

  (query! [db query]
    (try-query
     (cond
      (string? query)
      (jdbc/execute! (get-connection db-specs :default) [query])

      (sequential? query)
      (jdbc/execute! (get-connection db-specs :default) query)

      (fn? query)
      (query {} (get-connection db-specs :default))

      :else
      (jdbc/execute! (get-connection db-specs :default) (honeysql/format query)))))

  (query! [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query
       (cond
        (and (fn? query) (nil? args))
        (query {} {:connection (get-connection db-specs key)})

        (fn? query)
        (query args {:connection (get-connection db-specs key)})

        (and (string? query)
             (nil? args))
        (jdbc/execute! (get-connection db-specs key) [query])

        (string? query)
        (jdbc/execute! (get-connection db-specs key) (concat [query] args))

        (and (sequential? query)
             (nil? args))
        (jdbc/execute! (get-connection db-specs key) query)

        (sequential? query)
        (jdbc/execute! (get-connection db-specs key) (concat query args))

        (nil? args)
        (jdbc/execute! (get-connection db-specs key) (honeysql/format query))

        :else
        (jdbc/execute! (get-connection db-specs key) (honeysql/format query args))))))

  (query! [db key query args]
    (try-query-args
     (cond
      (fn? query)
      (query args {:connection (get-connection db-specs key)})

      (string? query)
      (jdbc/execute! (get-connection db-specs key) (concat [query] args))

      (sequential? query)
      (jdbc/execute! (get-connection db-specs key) (concat query args))

      :else
      (jdbc/execute! (get-connection db-specs key) (honeysql/format query args)))))

  (query<! [db query]
    (try-query
     (cond
      (string? query)
      (throw-msg "String is not allowed for query<!")

      (fn? query)
      (query {} (get-connection db-specs :default))

      :else
      (let [table (:insert-into query)
            values (:values query)]
        (apply jdbc/insert! (get-connection db-specs :default) table values)))))
  (query<! [db key? query]
    (let [[key query args] (if (get db-specs key?)
                             [key? query nil]
                             [:default key? query])]
      (try-query-args
       (cond
        (and (string? query)
             (nil? args))
        (throw-msg "String with no args is not allowed for query<!")

        (string? query)
        (apply jdbc/insert! (get-connection db-specs key) query args)

        (and (fn? query)
             (nil? args))
        (query {} {:connection (get-connection db-specs key)})

        (fn? query)
        (query args {:connection (get-connection db-specs key)})

        (nil? args)
        (let [table (:insert-into query)
              values (:values query)]
          (apply jdbc/insert! (get-connection db-specs key) table values))

        :else
        (let [table (:insert-into query)
              values (:values query)]
          (apply jdbc/insert! (get-connection db-specs :default) table values))))))
  (query<! [db key query args]
    (try-query-args
     (cond
      (string? query)
      (throw-msg "String is not allowed for query<!")

      (fn? query) (query args {:connection (get-connection db-specs key)})
      :else (let [table (:insert-into query)
                  values (:values query)]
              (apply jdbc/insert! (get-connection db-specs key) table values)))))

  (databases [db]
    (keys db-specs)))
