# ez-database

Handling database queries with different libraries. Support for yesql, honeysql, sequentials (string + params) and plain strings.

## rationale

[yesql](https://github.com/krisajenkins/yesql) is nice when I know the query beforehand. [Honey SQL](https://github.com/jkk/honeysql) is nice when I don't. Just a plain string with some args is sometimes the best option. And sometimes I really want to just send in a vector that's been reduced.

## protocol

```clojure
(defprotocol IEzDatabase
  (query [database query] [database key? query] [database key query args])
  (query! [database query] [database key? query] [database key query args])
  (query<! [database query] [database key? query] [database key query args])
  (databases [database]))
```

- database which is a record is always the first field.
- query always returns a result set.
- query! executes a command and does not return a result set.
- query<! executes a command and gives back a value. Used for insert

## Usage

Download from clojars
```clojure
[ez-database "0.4.0"]
```

Assuming a database with the following schema.

```sql
CREATE TABLE test (
  id integer
);
INSERT INTO test VALUES (0), (42);
```

```clojure
(require '[ez-database.core :as db])

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname "//localhost:5432/test"
              :user "user"
              :password "password"})

;; add extra database specs under different keys
;; default is a required key
(def db (db/map->EzDatabase {:db-specs {:default db-spec}}))
```

Given above code...

### query

#### with strings
```clojure
(db/query db "select * from test;") ;; => [{:id 0} {:id 42}]
(db/query db "select * from test where id > ?" [0]) ;; => [{:id 42}]
```

#### with sequentials
```clojure
(db/query db ["select * from test where id > ?" 0]) ;; => [{:id 42}]
```

#### with yesql functions
```sql
--name: sql-select-query
  SELECT * FROM test WHERE id > :id;
```

```clojure
(db/query db sql-select-query {:id 0});; => [{:id 42}]
```

#### with honeysql hashmaps

```clojure
(db/query db {:select [:*]
              :from [:test]
              :where [:> :id 0]}) ;; => [{:id 42}]
```

### execute (no return value asked for)

#### with string
```clojure
(db/query! db "delete from test;")
(db/query! db "delete from test where id > ?;" [0])
```

#### with sequential
```clojure
(db/query! db ["delete from test where id > ?;" 0])
(db/query! db ["delete from test where id > ?;"] [0])
```

#### with yesql functions
```sql
--name: sql-delete-higher-than!
  DELETE FROM test WHERE id > :id;
```

```clojure
(db/query! db sql-delete-higher-than! {:id 0})
```

#### with honeysql hashmaps

```clojure
(db/query! db {:delete-from :test
               :where [:> :id 0]})
```


### inserts

#### with strings
```clojure
;; return scalar value
(db/query<! db "insert into test values (-1);") ;; => [1]
;; return value
(db/query<! db "test" [{:id -1}]) ;; => [{:id -1}]
```

#### with sequentials
```clojure
;; return scalar value
(db/query<! db ["insert into test values (?);" -1]) ;; => [1]
```

#### with yesql functions
```sql
--name: sql-insert<!
  INSERT INTO test VALUES (:id);
```

Note that yesql functions returns the actual value, not a sequence of values.
``` clojure
(db/query<! db sql-insert<! {:id -1}) ;; => {:id -1}
```

#### with honeysql hashmaps

```clojure
(db/query<! db {:insert-into :test 
                :values [{:id -1}]}) ;; => [{:id -1}]
```

### databases

Returns a list of keys for the databases in the record.
```clojure
(db/database db) ;; => (:default)
```

### more than one database

Assuming databases :default and :foobar we can do selects against both of them.
```clojure
(require '[ez-database.core :as db])

(def db-spec {:classname "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname "//localhost:5432/test"
              :user "user"
              :password "password"})
              
(def db-spec-foo {:classname "org.postgresql.Driver"
                  :subprotocol "postgresql"
                  :subname "//localhost:5432/foobar"
                  :user "user"
                  :password "password"})

;; add extra database specs under different keys
;; default is a required key
(def db (db/map->EzDatabase {:db-specs {:default db-spec
                                        :foobar db-spec-foo}}))

(db/query db "select * from test;") ;; => [{:id 0} {:id 42}]
(db/query db :foobar "select * from foo where id > ?" [0]) ;; => [{:id 42 :what_p "How many bars does it take for foo to be happy?"}]
```

## transactions

```clojure
;; will fail because :id only takes integers
(db/with-transaction [db :default]
  (db/query! db {:insert-into :test :values [{:id 1}]})
  (db/query! db {:insert-into :test :values [{:id "asdf"}]}))
```

## query zipper

Stricly for [Honey SQL](https://github.com/jkk/honeysql) maps, the query zipper can provide optional arguments that can be cleaned up by the zipper,
happily avoid nil values which will be interpreted by HoneySQL as NULL. Use together with honeysql.helpers functions.

```clojure
(require '[ez-database.query :as query])
(requery '[honeysql.helpers :as sql.helpers]) 

(def pred? true)

(-> {:select [:*]
     :from [:test]
     :where [:> :id 0]}
     (query/swap pred? (sql.helpers/where [:or [:= :id 0]
                                               (query/optional true [:is :id nil])]))
     (query/clean))
     
;; will produce

{:select [:*]
 :from [:test]
 :where [:or [:= :id 0]
             [:is :id nil]]}
```

*query/optional* takes *[pred? & r]* as arguments

  - pred? is the predicate function
  - r is what will be filled in
  
  *query/swap* takes *[q pred? helper]* as arguments
  
  - q is the already existing query
  - pred? is the predicate function
  - helper is the helper function from honeysql.helpers


*query/clean* will clean up the query map from any nil values produced by the optional macro


## License

Copyright © 2015-2016 Emil Bengtsson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

---

Coram Deo
