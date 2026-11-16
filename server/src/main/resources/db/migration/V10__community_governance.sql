-- Additive migration: do not modify previously applied V1-V9 checksums.
ALTER TABLE article ADD governance_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE category ADD revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE governance_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 admin_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_digest CHAR(64) NOT NULL,
 target_type VARCHAR(16) NOT NULL,
 target_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 before_state VARCHAR(24) NOT NULL,
 after_state VARCHAR(24) NOT NULL,
 revision BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 FOREIGN KEY(admin_id) REFERENCES community_admin(id),
 UNIQUE KEY uq_governance_request(admin_id,request_id),
 INDEX idx_governance_target(target_type,target_id,id),
 CHECK(target_type IN ('ARTICLE','COMMENT','CATEGORY')),
 CHECK(revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE governance_article_hold (
 article_id BIGINT PRIMARY KEY,
 previous_state VARCHAR(24) NOT NULL,
 FOREIGN KEY(article_id) REFERENCES article(id),
 CHECK(previous_state IN ('PUBLISHED','AUTHOR_OFFLINE'))
) ENGINE=InnoDB;
