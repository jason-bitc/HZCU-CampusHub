-- Contact privacy is independent of profile replacement and published snapshots.
CREATE TABLE author_contact_setting (
  user_id BIGINT PRIMARY KEY,
  revision BIGINT NOT NULL,
  enabled BOOLEAN NOT NULL,
  contact_type VARCHAR(16) NULL,
  contact_value VARCHAR(2000) NULL,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  CONSTRAINT ck_contact_revision CHECK (revision>0),
  CONSTRAINT ck_contact_type CHECK (contact_type IS NULL OR contact_type IN ('WECHAT','QQ','WEB')),
  CONSTRAINT ck_contact_pair CHECK (
    (contact_type IS NULL AND contact_value IS NULL AND enabled=FALSE) OR
    (contact_type IS NOT NULL AND contact_value IS NOT NULL AND CHAR_LENGTH(contact_value)>0
      AND CHAR_LENGTH(contact_value)<=IF(contact_type='WEB',2000,200)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Historical internal identifiers deliberately have no cascading user/article FK.
-- Only revisions and finite states are retained, never old/new user content.
CREATE TABLE business_audit (
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  occurred_at DATETIME(6) NOT NULL,
  trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  object_type VARCHAR(16) NOT NULL,
  object_id BIGINT NOT NULL,
  before_revision BIGINT NOT NULL,
  after_revision BIGINT NOT NULL,
  before_state VARCHAR(24) NULL,
  after_state VARCHAR(24) NULL,
  result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  CONSTRAINT ck_audit_action CHECK (action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE')),
  CONSTRAINT ck_audit_object CHECK (object_type IN ('PROFILE','CONTACT','ARTICLE')),
  CONSTRAINT ck_audit_revisions CHECK (before_revision>=0 AND after_revision=before_revision+1),
  CONSTRAINT ck_audit_ids CHECK (actor_id>0 AND object_id>0),
  CONSTRAINT ck_audit_result CHECK (result='SUCCESS'),
  CONSTRAINT ck_audit_before CHECK (before_state IS NULL OR before_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED')),
  CONSTRAINT ck_audit_after CHECK (after_state IS NULL OR after_state IN ('ENABLED','DISABLED','DRAFT','PUBLISHED','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED')),
  INDEX idx_audit_trace(trace_id),
  INDEX idx_audit_actor(actor_id,occurred_at,event_id),
  INDEX idx_audit_object(object_type,object_id,occurred_at,event_id)
) ENGINE=InnoDB;
CREATE INDEX idx_author_public ON article(owner_user_id,state,first_published_at DESC,id DESC);
