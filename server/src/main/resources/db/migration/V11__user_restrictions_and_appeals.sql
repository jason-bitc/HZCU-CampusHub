CREATE TABLE community_restriction (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 scope VARCHAR(16) NOT NULL,
 enabled BOOLEAN NOT NULL,
 revision BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 admin_id BIGINT NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES platform_user(id),
 FOREIGN KEY(admin_id) REFERENCES community_admin(id),
 UNIQUE KEY uq_restriction_scope(user_id,scope),
 CHECK(scope IN ('PUBLISH','COMMENT')),
 CHECK(revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_restriction_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 admin_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 digest CHAR(64) NOT NULL,
 restriction_id BIGINT NOT NULL,
 before_enabled BOOLEAN NOT NULL,
 enabled BOOLEAN NOT NULL,
 revision BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 FOREIGN KEY(admin_id) REFERENCES community_admin(id),
 FOREIGN KEY(restriction_id) REFERENCES community_restriction(id),
 UNIQUE KEY uq_restriction_request(admin_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_appeal (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 digest CHAR(64) NOT NULL,
 restriction_id BIGINT NOT NULL,
 restriction_revision BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 revision BIGINT NOT NULL DEFAULT 1,
 reply VARCHAR(2000) NOT NULL DEFAULT '',
 decided_by BIGINT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES platform_user(id),
 FOREIGN KEY(restriction_id) REFERENCES community_restriction(id),
 FOREIGN KEY(decided_by) REFERENCES community_admin(id),
 UNIQUE KEY uq_appeal_request(user_id,request_id),
 CHECK(state IN ('OPEN','ACCEPTED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_appeal_receipt (
 admin_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 digest CHAR(64) NOT NULL,
 appeal_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL,
 PRIMARY KEY(admin_id,request_id),
 FOREIGN KEY(admin_id) REFERENCES community_admin(id),
 FOREIGN KEY(appeal_id) REFERENCES community_appeal(id)
) ENGINE=InnoDB;
