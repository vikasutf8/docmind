-- docmind postgres bootstrap. Runs once on first container start
-- (files in /docker-entrypoint-initdb.d). Safe to re-run: IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS hstore;
