-- Historical article.effective_* and count_origin remain unchanged.
-- Active relationship rows are the only source of current LIVE like counts.
CREATE TABLE article_like (
  user_id BIGINT NOT NULL,
  article_id BIGINT NOT NULL,
  active BOOLEAN NOT NULL,
  revision BIGINT NOT NULL,
  activated_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(user_id,article_id),
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  CONSTRAINT ck_like_active CHECK (active IN (0,1)),
  CONSTRAINT ck_like_revision CHECK (revision>0),
  INDEX idx_like_live(article_id,active,user_id)
) ENGINE=InnoDB;

CREATE TABLE article_favorite (
  user_id BIGINT NOT NULL,
  article_id BIGINT NOT NULL,
  active BOOLEAN NOT NULL,
  revision BIGINT NOT NULL,
  activated_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY(user_id,article_id),
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  CONSTRAINT ck_favorite_active CHECK (active IN (0,1)),
  CONSTRAINT ck_favorite_revision CHECK (revision>0),
  INDEX idx_favorite_page(user_id,active,activated_at DESC,article_id DESC)
) ENGINE=InnoDB;

ALTER TABLE business_audit
  DROP CHECK ck_audit_action,
  DROP CHECK ck_audit_object,
  ADD CONSTRAINT ck_audit_action CHECK (action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE','COMMENT_CREATE','COMMENT_DELETE','LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL')),
  ADD CONSTRAINT ck_audit_object CHECK (object_type IN ('PROFILE','CONTACT','ARTICLE','COMMENT','LIKE','FAVORITE'));
