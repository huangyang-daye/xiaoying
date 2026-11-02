ALTER TABLE production_task ADD COLUMN idempotency_key VARCHAR(128) NULL;
ALTER TABLE production_task ADD COLUMN request_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uq_task_idempotency ON production_task(owner_id,idempotency_key);
ALTER TABLE production_task ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE production_task ADD COLUMN max_attempts INT NOT NULL DEFAULT 3;
ALTER TABLE production_task ADD COLUMN retry_base_delay_ms INT NOT NULL DEFAULT 1000;
ALTER TABLE production_task ADD COLUMN retry_max_delay_ms INT NOT NULL DEFAULT 30000;
ALTER TABLE production_task ADD COLUMN next_attempt_at TIMESTAMP(6) NULL;
ALTER TABLE production_task ADD COLUMN lease_token VARCHAR(36) NULL;
ALTER TABLE production_task ADD COLUMN lease_expires_at TIMESTAMP(6) NULL;
ALTER TABLE production_task ADD COLUMN last_error_code VARCHAR(64) NULL;
ALTER TABLE production_task ADD COLUMN last_error_message VARCHAR(1000) NULL;
ALTER TABLE production_task ADD COLUMN transient_fail_at_stage VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE production_task ADD COLUMN transient_failure_count INT NOT NULL DEFAULT 0;
ALTER TABLE task_step ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
CREATE INDEX idx_task_due ON production_task(status,next_attempt_at,created_at,id);
CREATE INDEX idx_task_lease ON production_task(status,lease_expires_at);
CREATE TABLE task_attempt (
 task_id VARCHAR(36) NOT NULL, attempt_no INT NOT NULL, lease_token VARCHAR(36) NOT NULL,
 stage VARCHAR(32) NOT NULL, outcome VARCHAR(32) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL, finished_at TIMESTAMP(6) NULL,
 error_code VARCHAR(64) NULL, error_message VARCHAR(1000) NULL,
 PRIMARY KEY(task_id,attempt_no),
 FOREIGN KEY(task_id) REFERENCES production_task(id) ON DELETE CASCADE
);
