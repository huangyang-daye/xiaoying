CREATE TABLE app_user (
 id VARCHAR(32) PRIMARY KEY,
 username VARCHAR(32) NOT NULL,
 email VARCHAR(254) NOT NULL,
 password_hash VARCHAR(200) NOT NULL,
 display_name VARCHAR(40) NOT NULL,
 avatar_key VARCHAR(100),
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE (username),
 UNIQUE (email)
);
