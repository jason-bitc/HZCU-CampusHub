CREATE TABLE community_admin (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  login_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT ck_admin_enabled CHECK(enabled IN (0,1))
) ENGINE=InnoDB;
CREATE TABLE community_admin_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  FOREIGN KEY(admin_id) REFERENCES community_admin(id),
  INDEX idx_admin_session(admin_id,revoked_at)
) ENGINE=InnoDB;
CREATE TABLE community_admin_login_gate (
  name_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  failures INT NOT NULL DEFAULT 0,
  window_started DATETIME(6) NOT NULL,
  CONSTRAINT ck_admin_failures CHECK(failures>=0)
) ENGINE=InnoDB;

-- Shared late-acquired sequence lock orders case-history commits and stable snapshots.
INSERT INTO authoring_sequence(name,next_value) VALUES('community',1);
CREATE TABLE community_case (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(16) NOT NULL,
  category VARCHAR(32) NOT NULL,
  target_type VARCHAR(16) NULL,
  article_id BIGINT NULL,
  comment_id BIGINT NULL,
  article_version_id BIGINT NULL,
  description MEDIUMTEXT NOT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  revision BIGINT NOT NULL DEFAULT 1,
  assignee_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  FOREIGN KEY(comment_id) REFERENCES discussion_comment(id),
  FOREIGN KEY(article_version_id) REFERENCES article_version(id),
  FOREIGN KEY(assignee_id) REFERENCES community_admin(id),
  UNIQUE KEY uq_case_request(user_id,request_id),
  CONSTRAINT ck_case_kind CHECK(kind IN ('FEEDBACK','REPORT')),
  CONSTRAINT ck_case_category CHECK((kind='FEEDBACK' AND category IN ('BUG','SUGGESTION','OTHER')) OR (kind='REPORT' AND category IN ('ADVERTISEMENT','MISLEADING_CONTENT','OTHER'))),
  CONSTRAINT ck_case_target CHECK(
    (kind='FEEDBACK' AND target_type IS NULL AND article_id IS NULL AND comment_id IS NULL AND article_version_id IS NULL) OR
    (target_type IS NOT NULL AND target_type='ARTICLE' AND article_id IS NOT NULL AND article_version_id IS NOT NULL AND comment_id IS NULL) OR
    (target_type IS NOT NULL AND target_type='COMMENT' AND article_id IS NOT NULL AND article_version_id IS NOT NULL AND comment_id IS NOT NULL)),
  CONSTRAINT ck_case_state CHECK(state IN ('OPEN','PROCESSING','NEEDS_ACTION','CLOSED')),
  CONSTRAINT ck_case_revision CHECK(revision>0),
  CONSTRAINT ck_case_assignee CHECK((state='OPEN' AND assignee_id IS NULL) OR (state<>'OPEN' AND assignee_id IS NOT NULL)),
  INDEX idx_case_user(user_id,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_case_history (
  sequence_id BIGINT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  actor_domain VARCHAR(24) NOT NULL,
  actor_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL,
  state VARCHAR(24) NOT NULL,
  assignee_id BIGINT NULL,
  outcome VARCHAR(32) NULL,
  public_reply MEDIUMTEXT NOT NULL,
  internal_note MEDIUMTEXT NOT NULL,
  reason MEDIUMTEXT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  FOREIGN KEY(case_id) REFERENCES community_case(id),
  FOREIGN KEY(assignee_id) REFERENCES community_admin(id),
  UNIQUE KEY uq_case_revision(case_id,revision),
  CONSTRAINT ck_case_history_domain CHECK(actor_domain IN ('USER','COMMUNITY_ADMIN')),
  CONSTRAINT ck_case_history_state CHECK(state IN ('OPEN','PROCESSING','NEEDS_ACTION','CLOSED')),
  CONSTRAINT ck_case_history_action CHECK(action IN ('CASE_SUBMIT','CASE_CLAIM','CASE_RELEASE','CASE_DECISION')),
  INDEX idx_case_snapshot(case_id,sequence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_admin_receipt (
  admin_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  case_id BIGINT NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  revision BIGINT NOT NULL,
  state VARCHAR(24) NOT NULL,
  PRIMARY KEY(admin_id,request_id),
  FOREIGN KEY(admin_id) REFERENCES community_admin(id),
  FOREIGN KEY(case_id) REFERENCES community_case(id)
) ENGINE=InnoDB;
CREATE TABLE community_case_image (
  sequence_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mime_type VARCHAR(24) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  case_id BIGINT NULL,
  image_order INT NULL,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(case_id) REFERENCES community_case(id),
  UNIQUE KEY uq_case_image_request(user_id,request_id),
  UNIQUE KEY uq_case_image_order(case_id,image_order),
  CONSTRAINT ck_case_image_type CHECK(mime_type IN ('image/png','image/jpeg')),
  CONSTRAINT ck_case_image_size CHECK(size_bytes>0 AND width>0 AND height>0),
  CONSTRAINT ck_case_image_binding CHECK((case_id IS NULL AND image_order IS NULL) OR (case_id IS NOT NULL AND image_order>=0))
) ENGINE=InnoDB;
-- Only context visible at submission is captured; never join arbitrary drafts later.
CREATE TABLE community_case_comment (
  case_id BIGINT NOT NULL,
  context_role VARCHAR(16) NOT NULL,
  comment_id BIGINT NOT NULL,
  available BOOLEAN NOT NULL,
  body MEDIUMTEXT NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY(case_id,context_role),
  FOREIGN KEY(case_id) REFERENCES community_case(id),
  FOREIGN KEY(comment_id) REFERENCES discussion_comment(id),
  CONSTRAINT ck_case_comment_role CHECK(context_role IN ('TARGET','ROOT','REPLY_TO')),
  CONSTRAINT ck_case_comment_body CHECK((available=TRUE AND body IS NOT NULL AND created_at IS NOT NULL) OR (available=FALSE AND body IS NULL AND created_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_case_media (
  case_id BIGINT NOT NULL,
  image_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_kind VARCHAR(24) NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mime_type VARCHAR(24) NOT NULL,
  size_bytes BIGINT NOT NULL,
  PRIMARY KEY(case_id,image_id),
  FOREIGN KEY(case_id) REFERENCES community_case(id),
  CONSTRAINT ck_case_media_source CHECK(source_kind IN ('ARTICLE_IMAGE','COMMENT_IMAGE'))
) ENGINE=InnoDB;

ALTER TABLE business_audit
  ADD actor_domain VARCHAR(24) NOT NULL DEFAULT 'USER',
  ADD CONSTRAINT ck_audit_domain CHECK(actor_domain IN ('USER','COMMUNITY_ADMIN')),
  DROP CHECK ck_audit_action,
  DROP CHECK ck_audit_object,
  DROP CHECK ck_audit_before,
  DROP CHECK ck_audit_after,
  ADD CONSTRAINT ck_audit_action CHECK(action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE','COMMENT_CREATE','COMMENT_DELETE','LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL','READING_START','LOGIN_REWARD','READING_REWARD','ARTICLE_COIN','CASE_SUBMIT','CASE_IMAGE_UPLOAD','CASE_CLAIM','CASE_RELEASE','CASE_DECISION')),
  ADD CONSTRAINT ck_audit_object CHECK(object_type IN ('PROFILE','CONTACT','ARTICLE','COMMENT','LIKE','FAVORITE','READING','WALLET','CASE','CASE_IMAGE')),
  ADD CONSTRAINT ck_audit_before CHECK(before_state IS NULL OR before_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED','VISIBLE','OPEN','PROCESSING','NEEDS_ACTION','CLOSED')),
  ADD CONSTRAINT ck_audit_after CHECK(after_state IS NULL OR after_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED','VISIBLE','OPEN','PROCESSING','NEEDS_ACTION','CLOSED'));
CREATE INDEX idx_audit_domain_actor ON business_audit(actor_domain,actor_id,occurred_at,event_id);
