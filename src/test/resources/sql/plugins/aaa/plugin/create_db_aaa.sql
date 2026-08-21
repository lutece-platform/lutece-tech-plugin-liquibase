--liquibase formatted sql
--lutece runAfter:bbb
--changeset aaa:create_db_aaa
CREATE TABLE aaa_data (id INT);
