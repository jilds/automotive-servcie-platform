-- Runs once when the local database container is first created.
-- asp_owner owns the schema and runs migrations; asp_app is what the application uses and is bound by row-level security.
create role asp_owner login password 'asp_owner';
create role asp_app login password 'asp_app';
create database asp owner asp_owner;
\connect asp
alter schema public owner to asp_owner;
