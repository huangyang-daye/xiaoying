ALTER TABLE template ADD COLUMN cover_url VARCHAR(2048) NULL;
ALTER TABLE template ADD COLUMN sample_result_url VARCHAR(2048) NULL;
ALTER TABLE production_task ADD COLUMN sample_result_url VARCHAR(2048) NULL;
