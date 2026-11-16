INSERT INTO authoring_sequence(name,next_value) VALUES('comment',1);

CREATE TABLE discussion_comment (
  id BIGINT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  root_comment_id BIGINT NULL,
  reply_to_comment_id BIGINT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'VISIBLE',
  revision BIGINT NOT NULL DEFAULT 1,
  body MEDIUMTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_comment_article(article_id,id),
  UNIQUE KEY uq_comment_owner(article_id,user_id,id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id,root_comment_id) REFERENCES discussion_comment(article_id,id),
  FOREIGN KEY(article_id,reply_to_comment_id) REFERENCES discussion_comment(article_id,id),
  CONSTRAINT ck_comment_relation CHECK (
    (root_comment_id IS NULL AND reply_to_comment_id IS NULL) OR
    (root_comment_id IS NOT NULL AND reply_to_comment_id IS NOT NULL AND root_comment_id<>id AND reply_to_comment_id<>id)),
  CONSTRAINT ck_comment_state CHECK (state IN ('VISIBLE','DELETED','ADMIN_HIDDEN')),
  CONSTRAINT ck_comment_revision CHECK (revision>0),
  INDEX idx_comment_thread(article_id,root_comment_id,created_at,id),
  INDEX idx_comment_owner(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_create_receipt (
  user_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  article_id BIGINT NOT NULL,
  comment_id BIGINT NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY(user_id,request_id),
  FOREIGN KEY(article_id,user_id,comment_id) REFERENCES discussion_comment(article_id,user_id,id)
) ENGINE=InnoDB;

CREATE TABLE comment_image (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mime_type VARCHAR(24) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  comment_id BIGINT NULL,
  image_order INT NULL,
  UNIQUE KEY uq_comment_upload(user_id,request_id),
  UNIQUE KEY uq_comment_image_order(comment_id,image_order),
  FOREIGN KEY(article_id) REFERENCES article(id),
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id,user_id,comment_id) REFERENCES discussion_comment(article_id,user_id,id),
  CONSTRAINT ck_comment_image_type CHECK (mime_type IN ('image/jpeg','image/png')),
  CONSTRAINT ck_comment_image_size CHECK (size_bytes>0 AND width>0 AND height>0),
  CONSTRAINT ck_comment_image_binding CHECK ((comment_id IS NULL AND image_order IS NULL) OR (comment_id IS NOT NULL AND image_order>=0))
) ENGINE=InnoDB;

ALTER TABLE business_audit
  DROP CHECK ck_audit_action,
  DROP CHECK ck_audit_object,
  DROP CHECK ck_audit_before,
  DROP CHECK ck_audit_after,
  ADD CONSTRAINT ck_audit_action CHECK (action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE','COMMENT_CREATE','COMMENT_DELETE')),
  ADD CONSTRAINT ck_audit_object CHECK (object_type IN ('PROFILE','CONTACT','ARTICLE','COMMENT')),
  ADD CONSTRAINT ck_audit_before CHECK (before_state IS NULL OR before_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED','VISIBLE')),
  ADD CONSTRAINT ck_audit_after CHECK (after_state IS NULL OR after_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED','VISIBLE'));
