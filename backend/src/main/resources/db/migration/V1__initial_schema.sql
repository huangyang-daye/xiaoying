CREATE TABLE template_category (
 id VARCHAR(64) PRIMARY KEY, name VARCHAR(80) NOT NULL, sort_order INT NOT NULL
);
CREATE TABLE template (
 id VARCHAR(64) PRIMARY KEY, category_id VARCHAR(64) NOT NULL, title VARCHAR(80) NOT NULL,
 description VARCHAR(1000) NOT NULL, required_image_count INT NOT NULL,
 accepted_mime_types VARCHAR(100) NOT NULL, max_file_size_bytes BIGINT NOT NULL,
 sample_key VARCHAR(64) NOT NULL, FOREIGN KEY (category_id) REFERENCES template_category(id)
);
CREATE TABLE asset (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(32) NOT NULL, original_name VARCHAR(255) NOT NULL,
 storage_key VARCHAR(100) NOT NULL, mime_type VARCHAR(32) NOT NULL, size_bytes BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE production_task (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(32) NOT NULL, template_id VARCHAR(64) NOT NULL,
 template_title VARCHAR(80) NOT NULL, sample_key VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL, current_stage VARCHAR(32) NOT NULL, failure_stage VARCHAR(32),
 error_code VARCHAR(64), error_message VARCHAR(1000), fail_at_stage VARCHAR(32) NOT NULL,
 check_delay_ms INT NOT NULL, generation_delay_ms INT NOT NULL, assembly_delay_ms INT NOT NULL,
 result_key VARCHAR(100), result_size BIGINT, version BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, started_at TIMESTAMP(6), finished_at TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (template_id) REFERENCES template(id)
);
CREATE INDEX idx_task_owner ON production_task(owner_id, created_at, id);
CREATE INDEX idx_task_queue ON production_task(status, created_at, id);
CREATE TABLE task_asset (
 task_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL, slot_index INT NOT NULL,
 PRIMARY KEY(task_id, slot_index), UNIQUE(task_id, asset_id),
 FOREIGN KEY(task_id) REFERENCES production_task(id), FOREIGN KEY(asset_id) REFERENCES asset(id)
);
CREATE TABLE task_step (
 task_id VARCHAR(36) NOT NULL, stage VARCHAR(32) NOT NULL, sequence_no INT NOT NULL,
 status VARCHAR(20) NOT NULL, started_at TIMESTAMP(6), finished_at TIMESTAMP(6),
 error_code VARCHAR(64), error_message VARCHAR(1000), PRIMARY KEY(task_id, stage),
 FOREIGN KEY(task_id) REFERENCES production_task(id)
);
