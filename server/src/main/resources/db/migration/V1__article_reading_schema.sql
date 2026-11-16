-- UTC timestamps; only article reading storage, no identity/contact/interaction ledger tables.
CREATE TABLE article (
  id BIGINT NOT NULL PRIMARY KEY,
  state VARCHAR(24) NOT NULL,
  public_version_id BIGINT NULL,
  effective_coins BIGINT NOT NULL DEFAULT 0,
  effective_likes BIGINT NOT NULL DEFAULT 0,
  count_origin VARCHAR(24) NOT NULL DEFAULT 'DEMO',
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT ck_article_id CHECK (id > 0),
  CONSTRAINT ck_article_state CHECK (state IN ('PUBLISHED','DRAFT','AUTHOR_OFFLINE','ADMIN_HIDDEN','DELETED')),
  CONSTRAINT ck_counts CHECK (effective_coins >= 0 AND effective_likes >= 0),
  CONSTRAINT ck_count_origin CHECK (count_origin IN ('DEMO')),
  CONSTRAINT ck_public_version CHECK (state <> 'PUBLISHED' OR public_version_id IS NOT NULL),
  INDEX idx_article_recognized (state,effective_coins DESC,effective_likes DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE article_version (
  id BIGINT NOT NULL PRIMARY KEY,
  article_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  body_format VARCHAR(24) NOT NULL DEFAULT 'MARKDOWN',
  experience_stage VARCHAR(64) NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  source_note VARCHAR(2000) NOT NULL,
  published_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_version_article FOREIGN KEY (article_id) REFERENCES article(id),
  CONSTRAINT ck_version_number CHECK (version_number > 0),
  CONSTRAINT ck_body_format CHECK (body_format='MARKDOWN'),
  CONSTRAINT ck_source_type CHECK (source_type IN ('FIRST_HAND','CURATED','DEMO')),
  UNIQUE KEY uq_article_version (article_id,version_number),
  UNIQUE KEY uq_article_version_reference (article_id,id),
  INDEX idx_version_latest (published_at DESC,article_id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE article ADD CONSTRAINT fk_current_public_version
  FOREIGN KEY (id,public_version_id) REFERENCES article_version(article_id,id);

CREATE TABLE category (
  id BIGINT NOT NULL PRIMARY KEY,
  kind VARCHAR(24) NOT NULL,
  name VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  display_order INT NOT NULL DEFAULT 0,
  CONSTRAINT ck_category_kind CHECK (kind IN ('JOB','MAJOR','COMPANY','TOPIC')),
  UNIQUE KEY uq_category_name (kind,name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE article_version_category (
  version_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  PRIMARY KEY (version_id,category_id),
  CONSTRAINT fk_tag_version FOREIGN KEY (version_id) REFERENCES article_version(id),
  CONSTRAINT fk_tag_category FOREIGN KEY (category_id) REFERENCES category(id),
  INDEX idx_tag_filter (category_id,version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE article_source_link (
  id BIGINT NOT NULL PRIMARY KEY,
  version_id BIGINT NOT NULL,
  label VARCHAR(200) NOT NULL,
  url VARCHAR(2000) NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_source_version FOREIGN KEY (version_id) REFERENCES article_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
