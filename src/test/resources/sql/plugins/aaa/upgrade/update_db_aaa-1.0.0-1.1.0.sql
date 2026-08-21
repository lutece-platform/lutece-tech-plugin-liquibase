--liquibase formatted sql
--changeset aaa:update_db_aaa-1.0.0-1.1.0
ALTER TABLE aaa_data ADD COLUMN label VARCHAR(50);
