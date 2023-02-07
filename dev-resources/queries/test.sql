-- :name sql-fn-query :? :*

select id from test order by id;

--:name sql-fn-insert! :! :n

insert into test values (:id);

-- :name sql-fn-insert<! :insert :raw

insert into test values (:id);

-- :name sql-fn-delete! :! :n

delete from test where id = :id;
