-- Authentication storage. Deliberately independent of fictional article examples.
CREATE TABLE platform_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  account_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(24) NOT NULL DEFAULT 'USER',
  created_at DATETIME(6) NOT NULL,
  password_changed_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_user_email (email),
  CONSTRAINT ck_user_state CHECK (account_state IN ('ACTIVE','RESTRICTED')),
  CONSTRAINT ck_registration_role CHECK (role='USER')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_certification (
  user_id BIGINT NOT NULL PRIMARY KEY,
  method VARCHAR(24) NOT NULL DEFAULT 'CAMPUS_EMAIL',
  state VARCHAR(24) NOT NULL DEFAULT 'VALID',
  verified_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_cert_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
  CONSTRAINT ck_cert_method CHECK (method='CAMPUS_EMAIL'),
  CONSTRAINT ck_cert_state CHECK (state IN ('VALID','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
  UNIQUE KEY uq_session_digest (token_digest),
  INDEX idx_session_user (user_id,revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Serializes rate-limit checks and sends for the same canonical mailbox and purpose.
CREATE TABLE auth_email_gate (
  email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  last_attempt_at DATETIME(6) NULL,
  PRIMARY KEY (email,purpose),
  CONSTRAINT ck_gate_purpose CHECK (purpose IN ('REGISTER','PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_challenge (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  code_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(24) NOT NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  reset_user_id BIGINT NULL,
  CONSTRAINT fk_reset_user FOREIGN KEY (reset_user_id) REFERENCES platform_user(id),
  CONSTRAINT ck_challenge_purpose CHECK (purpose IN ('REGISTER','PASSWORD_RESET')),
  CONSTRAINT ck_challenge_status CHECK (status IN ('PENDING','SENT','FAILED','NOT_APPLICABLE','INVALIDATED','CONSUMED','LOCKED','EXPIRED')),
  CONSTRAINT ck_challenge_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
  INDEX idx_challenge_rate (email,purpose,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
