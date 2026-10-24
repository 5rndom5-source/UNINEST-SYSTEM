-- Apply once to an existing database after taking a backup.
ALTER TABLE room_images ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'building';
ALTER TABLE users ADD COLUMN password_changed_at DATETIME(3) NULL;
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reset_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
