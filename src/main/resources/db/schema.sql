CREATE TABLE IF NOT EXISTS activity (
  id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  total_quota INT NOT NULL,
  remaining_quota INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_remaining_nonneg CHECK (remaining_quota >= 0),
  CONSTRAINT chk_remaining_le_total CHECK (remaining_quota <= total_quota)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS registration (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_activity (user_id, activity_id),
  UNIQUE KEY uk_user_request (user_id, request_id),
  KEY idx_user_created (user_id, created_at, id),
  CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
  CONSTRAINT chk_request_id CHECK (request_id REGEXP '^[A-Za-z0-9_-]{1,64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
