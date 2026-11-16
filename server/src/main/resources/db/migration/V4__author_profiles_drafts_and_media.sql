-- Preserve the original demonstration articles without assigning them to real users.
ALTER TABLE article DROP CHECK ck_count_origin,
  ADD CONSTRAINT ck_count_origin CHECK (count_origin IN ('DEMO','NONE')),
  ADD COLUMN owner_user_id BIGINT NULL,
  ADD COLUMN first_published_at DATETIME(6) NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD CONSTRAINT fk_article_owner FOREIGN KEY (owner_user_id) REFERENCES platform_user(id),
  ADD INDEX idx_article_owner (owner_user_id,updated_at DESC,id DESC);
UPDATE article a LEFT JOIN article_version v ON v.id=a.public_version_id
  SET a.first_published_at=v.published_at,a.updated_at=COALESCE(v.published_at,a.created_at);
ALTER TABLE article_version ADD COLUMN updated_at DATETIME(6) NULL;
UPDATE article_version SET updated_at=published_at;

CREATE TABLE authoring_sequence (
  name VARCHAR(24) PRIMARY KEY,
  next_value BIGINT NOT NULL
) ENGINE=InnoDB;
INSERT INTO authoring_sequence SELECT 'article',COALESCE(MAX(id),0)+1 FROM article;
INSERT INTO authoring_sequence SELECT 'version',COALESCE(MAX(id),0)+1 FROM article_version;
INSERT INTO authoring_sequence SELECT 'source',COALESCE(MAX(id),0)+1 FROM article_source_link;

CREATE TABLE author_profile (
  user_id BIGINT PRIMARY KEY,
  revision BIGINT NOT NULL DEFAULT 0,
  nickname VARCHAR(40) NOT NULL DEFAULT '城院同学',
  major_id BIGINT NULL,
  current_stage VARCHAR(24) NOT NULL DEFAULT 'UNSET',
  bio VARCHAR(1000) NOT NULL DEFAULT '',
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
  CONSTRAINT fk_profile_major FOREIGN KEY (major_id) REFERENCES category(id),
  CONSTRAINT ck_profile_stage CHECK (current_stage IN ('UNSET','ENROLLED','GRADUATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE article_draft (
  article_id BIGINT PRIMARY KEY,
  revision BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(200) NOT NULL DEFAULT '',
  summary VARCHAR(1000) NOT NULL DEFAULT '',
  body MEDIUMTEXT NOT NULL,
  experience_stage VARCHAR(64) NOT NULL DEFAULT '',
  source_type VARCHAR(24) NULL,
  source_note VARCHAR(2000) NOT NULL DEFAULT '',
  CONSTRAINT fk_draft_article FOREIGN KEY (article_id) REFERENCES article(id),
  CONSTRAINT ck_draft_source CHECK (source_type IS NULL OR source_type IN ('DEMO','FIRST_HAND','CURATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE article_draft_category (
  article_id BIGINT NOT NULL,category_id BIGINT NOT NULL,
  PRIMARY KEY(article_id,category_id),
  FOREIGN KEY(article_id) REFERENCES article_draft(article_id),
  FOREIGN KEY(category_id) REFERENCES category(id)
) ENGINE=InnoDB;
CREATE TABLE article_draft_source_link (
  article_id BIGINT NOT NULL,link_order INT NOT NULL,
  label VARCHAR(200) NOT NULL,url VARCHAR(2000) NOT NULL,
  PRIMARY KEY(article_id,link_order),
  FOREIGN KEY(article_id) REFERENCES article_draft(article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE author_create_request (
  user_id BIGINT NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,article_id BIGINT NOT NULL,
  PRIMARY KEY(user_id,request_id),
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id)
) ENGINE=InnoDB;
CREATE TABLE article_publish_request (
  article_id BIGINT NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  expected_revision BIGINT NOT NULL,result_revision BIGINT NOT NULL,version_id BIGINT NOT NULL,
  PRIMARY KEY(article_id,request_id),
  FOREIGN KEY(article_id,version_id) REFERENCES article_version(article_id,id)
) ENGINE=InnoDB;

CREATE TABLE media_image (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  article_id BIGINT NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mime_type VARCHAR(24) NOT NULL,
  size_bytes BIGINT NOT NULL,width INT NOT NULL,height INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_upload_request(article_id,request_id),
  UNIQUE KEY uq_image_article(article_id,id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  CONSTRAINT ck_image_type CHECK (mime_type IN ('image/png','image/jpeg'))
) ENGINE=InnoDB;
CREATE TABLE article_draft_image (
  article_id BIGINT NOT NULL,image_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY(article_id,image_id),
  FOREIGN KEY(article_id) REFERENCES article_draft(article_id),
  FOREIGN KEY(article_id,image_id) REFERENCES media_image(article_id,id)
) ENGINE=InnoDB;
CREATE TABLE article_version_image (
  article_id BIGINT NOT NULL,version_id BIGINT NOT NULL,image_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY(version_id,image_id),
  FOREIGN KEY(article_id,version_id) REFERENCES article_version(article_id,id),
  FOREIGN KEY(article_id,image_id) REFERENCES media_image(article_id,id)
) ENGINE=InnoDB;
