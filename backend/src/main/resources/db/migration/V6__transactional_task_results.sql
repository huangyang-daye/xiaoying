-- Result content and the SUCCEEDED transition commit atomically in InnoDB.
CREATE TABLE task_result (
 task_id VARCHAR(36) PRIMARY KEY,
 content MEDIUMBLOB NOT NULL,
 FOREIGN KEY (task_id) REFERENCES production_task(id) ON DELETE CASCADE
);
