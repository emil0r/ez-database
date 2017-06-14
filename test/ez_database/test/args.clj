(ns ez-database.test.args
  (:require [ez-database.core :refer [get-args]]
            [midje.sweet :refer :all]))


(let [query "select * from foobar where id > ?;"
      opts ^:opts {:transform [:foo :foo/bar]}
      key :foobar
      args [100]
      db-specs {:foobar true}]
 (fact
  "query, opts, key, args"
  (fact "query, nil, nil, nil"
        (get-args db-specs query nil nil nil) => [query nil :default nil])
  (fact "query, opts, nil, nil"
        (get-args db-specs query opts nil nil) => [query opts :default nil])
  (fact "opts, opts, opts, query"
        (get-args db-specs opts opts opts query) => [query opts :default nil])
  (fact "opts, query, opts, args"
        (get-args db-specs opts query opts args) => [query opts :default args])
  (fact "query, nil, key, nil"
        (get-args db-specs query nil key nil) => [query nil key nil])
  (fact "query, key, key, nil"
        (get-args db-specs query key key nil) => [query nil key nil])
  (fact "query, opts, opts, nil"
        (get-args db-specs query opts opts nil) => [query opts :default nil])
  (fact "query, opts, nil, args"
        (get-args db-specs query opts nil args) => [query opts :default args])
  (fact "query, nil, key, args"
        (get-args db-specs query nil key args) => [query nil key args])
  (fact "args, query, query, args"
        (get-args db-specs query query query args) => [query nil :default args])
  (fact "query, opts, key, args"
        (get-args db-specs query opts key args) => [query opts key args])
  (fact "query, opts, key, query"
        (get-args db-specs query opts key query) => [query opts key nil])))
