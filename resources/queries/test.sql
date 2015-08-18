--name: sql-fn-query

select id from test order by id;

--name: sql-fn-insert!

insert into test values (:id);

--name: sql-fn-insert<!

insert into test values (:id);

--name: sql-fn-delete!

delete from test where id = :id;
