(ns ez-database.core
  (:require [clojure.string :as str]
            [ez-database.transform :as transform]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]
            [honey.sql :as honeysql]))

(defprotocol IEzDatabase
  (query [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (query! [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (query<! [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (databases [database]))

(def ^{:private true
       :dynamic true} *register-opts* (atom {}))

(def ^{:private true
       :dynamic true} *register-queries* (atom {}))

(defn register-opts! [k opts]
  (swap! *register-opts* assoc k opts))
(defn unregister-opts! [k]
  (swap! *register-opts* dissoc k))
(defn get-opts [opts]
  (if (keyword? opts)
    (get @*register-opts* opts)
    opts))

(defn- query-kw? [x]
  ;; In order to qualify as a query that's been registered it needs to start with query.
  ;; This is because keywords are also used for specifying which database the query should be
  ;; run against
  (if (keyword? x)
    (if-let [x-ns (namespace x)]
      (str/starts-with? x-ns "query"))))

(defn register-query! [k [opts key query :as data]]
  (assert (query-kw? k) "k must be a namespaced keyword starting with query like this :query/test")
  (assert (vector? data) "query must be a vector of [opts key query]")
  (assert (or (nil? opts) (keyword? opts) (and (map? opts) (:opts (meta opts)))) "opts must either be nil, a keyword or a map with :opts meta set to true")
  (assert (or (nil? key) (keyword? key)) "key must be either nil or a keyword")
  (assert (or (fn? query) (map? query) (string? query)) "query must be either a function, a map or a string")
  (swap! *register-queries* assoc k data))
(defn unregister-query! [k]
  (swap! *register-queries* dissoc k))
(defn clear-queries! []
  (reset! *register-queries* {}))
(defn get-query [k]
  (get @*register-queries* k))


;; multimethod for handling of returned values, post query
(def post-query nil)
(defmulti post-query (fn [k v db values] k))
(defmethod post-query [:transformation :post] [_ [from to opts] _ values]
  (cond (map? values)
        (transform/transform opts from to values)

        (sequential? values)
        (map #(transform/transform opts from to %) values)

        :else
        values))
(defmethod post-query [:remove-ks :post] [_ ks _ values]
  (cond (map? values)
        (apply dissoc values ks)

        (sequential? values)
        (map #(apply dissoc % ks) values)

        :else
        values))
(defmethod post-query [:remove :post] [_ pred _ values]
  (cond (map? values)
        (into {} (remove pred values))

        (sequential? values)
        (map #(into {} (remove pred %)) values)

        :else
        values))
(defmethod post-query [:filter :post] [_ pred _ values]
  (cond (map? values)
        (into {} (filter pred values))

        (sequential? values)
        (map #(into {} (filter pred %)) values)

        :else
        values))
(defmethod post-query :default [_ _ _ values]
  values)
(defn- run-post-query [db opts values]
  (if opts
    (reduce (fn [out [k v]]
              (post-query k v db out))
            values opts)
    values))

;; multimethod for args, pre query
(def process-args nil)
(defmulti process-args (fn [k v db args] k))
(defmethod process-args [:transformation :pre] [_ [from to opts] _ args]
  (cond (map? args)
        (transform/transform opts from to args)

        (sequential? args)
        (map #(transform/transform opts from to %) args)

        :else
        args))
(defmethod process-args [:remove-ks :pre] [_ ks _ args]
  (cond (map? args)
        (apply dissoc args ks)

        (sequential? args)
        (map #(apply dissoc % ks) args)

        :else
        args))
(defmethod process-args [:remove :pre] [_ pred _ args]
  (cond (map? args)
        (into {} (remove pred args))

        (sequential? args)
        (map #(into {} (remove pred %)) args)

        :else
        args))
(defmethod process-args [:filter :pre] [_ pred _ args]
  (cond (map? args)
        (into {} (filter pred args))

        (sequential? args)
        (map #(into {} (filter pred %)) args)

        :else
        args))
(defmethod process-args :default [_ _ _ args]
  args)
(defn- run-process-args [db opts args]
  (if opts
    (reduce (fn [out [k v]]
              (process-args k v db out))
            args opts)
    args))

(def process-query nil)
(defmulti process-query (fn [k & _] k))
(defmethod process-query :default [_ _ _ out]
  out)
(defn- run-process-query [db opts query args]
  (if opts
    (reduce (fn [out [k v]]
              (process-query k v db out))
            [query args] opts)
    [query args]))

(defn get-args [{:keys [db-specs] :as db} query? opts? key? args?]
  (if (query-kw? query?)
    (if-let [[opts key query :as data] (get @*register-queries* query?)]
      (let [-opts (get @*register-opts* opts opts)
            args (run-process-args db -opts (cond (some? args?) args?
                                               (some? key?) key?
                                               (some? opts?) opts?
                                               :else nil))
            [-query -args] (run-process-query db -opts query args)]
       [-query -opts (or key :default) -args])
      (throw (ex-info "Missing registered query" {:query query?})))
    (let [reg-opts @*register-opts*
          opts (cond (contains? reg-opts opts?) (get reg-opts opts?)
                     (contains? reg-opts key?) (get reg-opts key?)
                     (contains? reg-opts query?) (get reg-opts query?)
                     (contains? reg-opts args?) (get reg-opts args?)
                     (:opts (meta opts?)) opts?
                     (:opts (meta key?)) key?
                     (:opts (meta query?)) query?
                     (:opts (meta args?)) args?
                     :else nil)
          key (cond (get db-specs opts?) opts?
                    (get db-specs key?) key?
                    :else nil)
          query (->> [key? opts? query? args?]
                     (remove #(or (nil? %) (contains? reg-opts %) (= % key) (= % opts)))
                     first)

          args (run-process-args db opts (->> [args? query? key? opts?]
                                           (remove (into #{} (remove nil? [key opts query])))
                                           (remove #(contains? reg-opts %))
                                           first))
          [-query -args] (run-process-query db opts query args)]
      [-query opts (or key :default) -args])))

(defprotocol IEzQuery
  (run-query [query database opts] [query database opts args])
  (run-query! [query database opts] [query database opts args])
  (run-query<! [query database opts] [query database opts args]))

(def ^:dynamic *connection* nil)

(defn get-connection [db-specs key]
  (if-not (nil? *connection*)
    *connection*
    (get db-specs key)))

(defmacro with-transaction [[db k opts :as binding] & body]
  `(jdbc/transact (get-connection (:db-specs ~db) ~k)
                   (^{:once true} fn* [~'con]
                    (binding [*connection* ~'con]
                      ~@body)
                    ~(or opts {}))))

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

(defn- generated-query [query]
  (if (map? query)
    {:generated-query (honeysql/format query)}))

(defmacro try-query [& body]
  `(try
     ~@body
     (catch Exception ~'e
       (throw (ex-info "ez-database try-query failed"
                       (merge
                        {:type ::try-query
                         :exception ~'e
                         :messages (get-causes-messages ~'e)
                         :query ~'-query}
                        (generated-query ~'-query)))))))

(defmacro try-query-args [& body]
  `(try
     ~@body
     (catch Exception ~'e
       (throw (ex-info "ez-database try-query-args failed"
                       (merge
                        {:type ::try-query-args
                         :exception ~'e
                         :messages (get-causes-messages ~'e)
                         :query ~'-query
                         :args ~'args}
                        (generated-query ~'-query)))))))

(defn throw-msg [msg & args]
  (throw (ex-info msg
                  {:type ::error-msg
                   :args args})))

(defn- prep-insert-multi! [query]
  (let [table (:insert-into query)
        [columns values] (let [values (:values query)
                               ?columns (first values)]
                           (cond (map? ?columns)
                                 [(keys ?columns) (map vals values)]
                                 (sequential? ?columns)
                                 [?columns (rest values)]
                                 :else
                                 nil))]
    [table columns values]))

(extend-protocol IEzQuery

  nil
  (run-query
    ([query db opts]
     (throw-msg "nil can't be run as a query"))
    ([query db opts args]
     (throw-msg "nil can't be run as a query")))
  (run-query!
    ([query db opts]
     (throw-msg "nil can't be run as a query!"))
    ([query db opts args]
     (throw-msg "nil can't be run as a query!")))
  (run-query<!
    ([query db opts]
     (throw-msg "nil can't be run as a query<!"))
    ([query db opts args]
     (throw-msg "nil can't be run as a query<!")))

  java.lang.String
  (run-query
    ([query db opts]
     (jdbc/execute! db [query] opts))
    ([query db opts args]
     (jdbc/execute! db (concat [query] args) opts)))
  (run-query!
    ([query db opts]
     (jdbc/execute! db [query] opts))
    ([query db opts args]
     (jdbc/execute! db (concat [query] args) opts)))
  (run-query<!
    ([query db opts]
     (throw-msg "String is not allowed for query<!"))
    ([query db opts args]
     (throw-msg "String is not allowed for query<!")))

  clojure.lang.Sequential
  (run-query
    ([query db opts]
     (jdbc/execute! db query opts))
    ([query db opts args]
     (jdbc/execute! db (concat query args) opts)))
  (run-query!
    ([query db opts]
     (jdbc/execute! db query opts))
    ([query db opts args]
     (jdbc/execute! db (concat query args) opts)))
  (run-query<!
    ([query db opts]
     (throw-msg "Sequence is not allowed for query<!"))
    ([query db opts args]
     (throw-msg "Sequence is not allowed for query<!")))

  clojure.lang.IPersistentMap
  (run-query
    ([query db opts]
     (jdbc/execute! db (honeysql/format query) opts))
    ([query db opts args]
     (jdbc/execute! db (honeysql/format query args) opts)))
  (run-query!
    ([query db opts]
     (jdbc/execute! db (honeysql/format query) opts))
    ([query db opts args]
     (jdbc/execute! db (honeysql/format query args) opts)))
  (run-query<!
    ([query db opts]
     (let [[table columns values] (prep-insert-multi! query)]
       (sql/insert-multi! db table columns values)))
    ([query db opts args]
     (let [[table columns values] (prep-insert-multi! query)]
       (sql/insert-multi! db table columns values args))))

  clojure.lang.Fn
  (run-query
    ([query db opts]
     (query db {}))
    ([query db opts args]
     (query db args)))
  (run-query!
    ([query db opts]
     (query db {}))
    ([query db opts args]
     (query db args)))
  (run-query<!
    ([query db opts]
     (query db {}))
    ([query db opts args]
     (query db args))))


(defrecord EzDatabase [db-specs ezdb-opts]
  IEzDatabase

  (query [db -query]
    (try-query
     (if (keyword? -query)
       (apply query db (get-query -query))
       (run-query -query (get-connection db-specs :default) ezdb-opts))))

  (query [db opts-key? -query]
    (let [[-query opts key args] (get-args db opts-key? opts-key? opts-key? -query)]
      (try-query-args
       (run-post-query db opts
                       (if (nil? args)
                         (run-query -query (get-connection db-specs key) ezdb-opts)
                         (run-query -query (get-connection db-specs key) ezdb-opts args))))))

  (query [db opts? key? -query]
    (let [[-query opts key args] (get-args db key? opts? key? -query)]
      (try-query-args
       (run-post-query db ezdb-opts opts
                       (if (nil? args)
                         (run-query -query (get-connection db-specs key) ezdb-opts)
                         (run-query -query (get-connection db-specs key) ezdb-opts args))))))

  (query [db opts key -query args]
    (let [opts (get-opts opts)]
      (try-query-args
       (run-post-query db ezdb-opts opts
                       (run-query -query (get-connection db-specs key) (run-process-args db opts args))))))

  (query! [db -query]
    (try-query
     (if (keyword? -query)
       (apply query! db (get-query -query))
       (run-query! -query (get-connection db-specs :default) ezdb-opts))))

  (query! [db opts-key? -query]
    (let [[-query opts key args] (get-args db opts-key? opts-key? opts-key? -query)]
      (try-query
       (if (nil? args)
         (run-query! -query (get-connection db-specs key) ezdb-opts)
         (run-query! -query (get-connection db-specs key) ezdb-opts args)))))

  (query! [db opts? key? -query]
    (let [[-query opts key args] (get-args db key? opts? key? -query)]
      (try-query
       (if (nil? args)
         (run-query! -query (get-connection db-specs key) ezdb-opts)
         (run-query! -query (get-connection db-specs key) ezdb-opts args)))))

  (query! [db opts key -query args]
    (try-query-args
     (run-query! -query (get-connection db-specs key) (run-process-args db (get-opts opts) args))))

  (query<! [db -query]
    (try-query
     (if (keyword? -query)
       (apply query<! db (get-query -query))
       (run-query<! -query (get-connection db-specs :default) ezdb-opts))))
  (query<! [db opts-key? -query]
    (let [[-query opts key args] (get-args db opts-key? opts-key? opts-key? -query)]
      (try-query-args
       (run-post-query
        db opts
        (if (nil? args)
          (run-query<! -query (get-connection db-specs key) ezdb-opts)
          (run-query<! -query (get-connection db-specs key) ezdb-opts args))))))
  (query<! [db opts? key? -query]
    (let [[-query opts key args] (get-args db key? opts? key? -query)]
      (try-query-args
       (run-post-query
        db opts
        (if (nil? args)
          (run-query<! -query (get-connection db-specs key) ezdb-opts)
          (run-query<! -query (get-connection db-specs key) ezdb-opts args))))))
  (query<! [db opts key -query args]
    (let [opts (get-opts opts)]
      (try-query-args
       (run-post-query
        db opts
        (run-query<! -query (get-connection db-specs key) ezdb-opts (run-process-args db opts args))))))

  (databases [db]
    (keys db-specs)))
