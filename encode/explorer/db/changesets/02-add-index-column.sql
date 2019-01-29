--liquibase formatted sql

--changeset bencarlin:02
ALTER TABLE files
  ADD file_index_gs_path VARCHAR(250) NULL;

