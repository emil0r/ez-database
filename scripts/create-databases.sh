#!/bin/bash

psql --username "$POSTGRES_USER" <<EOSQL
 CREATE DATABASE ezdb1;
 ALTER DATABASE ezdb1 OWNER TO $POSTGRES_USER;
EOSQL

psql --username "$POSTGRES_USER" <<EOSQL
 CREATE DATABASE ezdb2;
 ALTER DATABASE ezdb2 OWNER TO $POSTGRES_USER;
EOSQL
