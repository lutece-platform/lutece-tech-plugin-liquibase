--liquibase formatted sql
--lutece runAfter:ccc
--changeset bbb:create_db_bbb
CREATE TABLE bbb_data (id INT);
