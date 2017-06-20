# ez-database

Handling database queries with different libraries. Support for yesql, honeysql, sequentials (string + params) and plain strings.

## rationale

[yesql](https://github.com/krisajenkins/yesql) is nice when I know the query beforehand. [Honey SQL](https://github.com/jkk/honeysql) is nice when I don't. Just a plain string with some args is sometimes the best option. And sometimes I really want to just send in a vector that's been reduced.

## protocol

```clojure
(defprotocol IEzDatabase
  (query [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (query! [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (query<! [database query] [database opts-key? query] [database opts? key? query] [database opts key query args])
  (databases [database]))
```

- database which is a record is always the first field.
- query always returns a result set.
- query! executes a command and returns number of rows affected
- query<! executes a command and gives back the values of what's been inserted. Used for insert

## Usage

Download from clojars
```clojure
[ez-database "0.6.0"]
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

## post and pre functions

Core now has the multimethods of post-query and pre-query. These functions are run on any data that goes into the database and after it comes out. Initiated via the opts running one of the query commands. See implementation details in core.clj for further details.

post and pre functions are applied using an opts map.

```clojure
 (db/query db 
           :^opts {[:remove-ks :post] #{:id}} 
           :db-key 
           {:select [:*]
            :from [:test]})
```

Ez-database comes with the following pre-defined functions. 

- [:remove-ks :post] #{:ks :in :here}
- [:remove-ks :pre] #{:ks :in :here}
- [:remove :post] pred-fn
- [:remove :pre] pred-fn
- [:filter :post] pred-fn
- [:filter :pre] pred-fn

:remove and :filter applies a remove and filter over each returned value using the pred-fn as the pred to apply. Remember that you will receive a [k v] pair into the function if there are maps involved (say, returned values from the database).

*Notice that if you combine any of these with [:transformation :pre] or [:transformation :post] it can be an idea to use an array-map instead of a hash-map so that you can control the order in which things happen.*

## transformations

One pre and post function is :transformation which can transform the values of incoming and outgoing values according to the specified transformation. Supports both change of keys and values.

```clojure
;; we have
;; 1) a database table named users that has the columns :id, :first_name and :last_name
;; 2) a spec :user/user under the ns user that specifies :user/id, :user/first-name and :user/last-name

;; -- inside user.clj --

'(require [clojure.alpha.spec :as s])

(s/def ::id integer?)
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::user (s/keys-of :req [::id
                               ::first-name
                               ::last-name]))

;; -- inside an init file of some sort --
'(require [ez-database.core :as db]
          [ez-database.transform :as transform]
          [example.user :as user])

;; the two transformation keys are completely arbitrary and can be named anything
(transform/add :user ::user/user
               [:id         ::user/id]
               [:first_name ::user/first-name]
               [:last_name  ::user/last-name])
               
(let [db (get-db)]
  (db/query db
            ;; transformations can use [:transformation :pre]
            ;; and [:transformation :post] in the opts map
            ;; pre is applied to any args sent in to the db
            ;; and post is applied to any values retrieved from the
            ;; database
            ^:opts {[:transformation :post] 
                    [:user ::user/user] 
                    ;; an optional opts map can
                    ;; be sent in to the transformation
                    { ;; allow nil values? defaults to true
                      ;; can be set to boolean or a set of keywords
                     :nil #{:foo :bar :baz} ;; <-- will be allowed to be nil
                                      
                      ;; optional validation against a spec
                     :validation ::user/user}]}
            {:select [:id :first_name :last_name]
             :from [:users]}))
```

## License

Copyright © 2015-2017 Emil Bengtsson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

---

Coram Deo
