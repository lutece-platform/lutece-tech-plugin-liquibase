--liquibase formatted sql
--changeset ccc:update_db_ccc-1.0.0-1.1.0
ALTER TABLE ccc_data ADD COLUMN label VARCHAR(50);
