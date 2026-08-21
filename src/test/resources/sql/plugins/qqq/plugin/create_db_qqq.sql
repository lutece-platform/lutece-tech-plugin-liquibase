--liquibase formatted sql
--lutece runAfter:doesnotexist
--changeset qqq:create_db_qqq
CREATE TABLE qqq_data (id INT);
