CREATE TABLE contribution_invalidation (
  coin_sequence_id BIGINT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  admin_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reason VARCHAR(2000) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  FOREIGN KEY (coin_sequence_id) REFERENCES coin_event(sequence_id),
  FOREIGN KEY (case_id) REFERENCES community_case(id),
  FOREIGN KEY (admin_id) REFERENCES community_admin(id),
  UNIQUE KEY uq_contribution_invalidation_request (admin_id, request_id),
  INDEX idx_contribution_invalidation_case (case_id),
  CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE business_audit
  DROP CHECK ck_audit_action,
  DROP CHECK ck_audit_object,
  ADD CONSTRAINT ck_audit_action CHECK(action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE','COMMENT_CREATE','COMMENT_DELETE','LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL','READING_START','LOGIN_REWARD','READING_REWARD','ARTICLE_COIN','CASE_SUBMIT','CASE_IMAGE_UPLOAD','CASE_CLAIM','CASE_RELEASE','CASE_DECISION','CONTRIBUTION_INVALIDATE')),
  ADD CONSTRAINT ck_audit_object CHECK(object_type IN ('PROFILE','CONTACT','ARTICLE','COMMENT','LIKE','FAVORITE','READING','WALLET','CASE','CASE_IMAGE','CONTRIBUTION'));
