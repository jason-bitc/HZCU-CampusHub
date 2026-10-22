-- Immutable economic facts; no historical article counters or content are changed.
CREATE TABLE coin_wallet (
  user_id BIGINT PRIMARY KEY,
  balance BIGINT NOT NULL DEFAULT 0,
  revision BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  CONSTRAINT ck_wallet_balance CHECK(balance>=0),
  CONSTRAINT ck_wallet_revision CHECK(revision>=0)
) ENGINE=InnoDB;

CREATE TABLE coin_reading_session (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id BIGINT NOT NULL,
  article_id BIGINT NOT NULL,
  business_date DATE NOT NULL,
  started_at DATETIME(6) NOT NULL,
  required_seconds INT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  UNIQUE KEY uq_reading_day(user_id,article_id,business_date),
  CONSTRAINT ck_reading_seconds CHECK(required_seconds BETWEEN 1 AND 86400),
  INDEX idx_reading_user_date(user_id,business_date,started_at,id)
) ENGINE=InnoDB;

-- This is also the private ledger and durable successful-request receipt.
-- Wallet revision orders a user's commits; sequence supplies a pagination high-water mark.
CREATE TABLE coin_event (
  sequence_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  business_date DATE NOT NULL,
  reward_date DATE NULL,
  article_id BIGINT NULL,
  author_id BIGINT NULL,
  reading_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  delta BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  wallet_revision BIGINT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  login_reward INT NOT NULL,
  reading_reward INT NOT NULL,
  reading_seconds INT NOT NULL,
  article_limit INT NOT NULL,
  rule_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  effective BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY(user_id) REFERENCES platform_user(id),
  FOREIGN KEY(article_id) REFERENCES article(id),
  FOREIGN KEY(author_id) REFERENCES platform_user(id),
  FOREIGN KEY(reading_session_id) REFERENCES coin_reading_session(id),
  UNIQUE KEY uq_coin_event(event_id),
  UNIQUE KEY uq_coin_request(user_id,action,request_id),
  UNIQUE KEY uq_coin_reward_day(user_id,action,reward_date),
  UNIQUE KEY uq_wallet_event_revision(user_id,wallet_revision),
  CONSTRAINT ck_coin_action CHECK(action IN ('LOGIN_REWARD','READING_REWARD','ARTICLE_COIN')),
  CONSTRAINT ck_coin_balance CHECK(balance_after>=0 AND wallet_revision>0),
  CONSTRAINT ck_coin_effective CHECK(effective IN (0,1)),
  CONSTRAINT ck_coin_fact CHECK(
    (action='LOGIN_REWARD' AND delta=login_reward AND reward_date IS NOT NULL AND reward_date=business_date AND article_id IS NULL AND author_id IS NULL AND reading_session_id IS NULL) OR
    (action='READING_REWARD' AND delta=reading_reward AND reward_date IS NOT NULL AND reward_date=business_date AND article_id IS NOT NULL AND author_id IS NULL AND reading_session_id IS NOT NULL) OR
    (action='ARTICLE_COIN' AND delta<0 AND -delta<=article_limit AND reward_date IS NULL AND article_id IS NOT NULL AND author_id IS NOT NULL AND author_id<>user_id AND reading_session_id IS NULL)),
  CONSTRAINT ck_coin_rules CHECK(login_reward BETWEEN 1 AND 1000 AND reading_reward BETWEEN 1 AND 1000 AND reading_seconds BETWEEN 1 AND 86400 AND article_limit BETWEEN 1 AND 1000),
  INDEX idx_coin_ledger(user_id,sequence_id DESC),
  INDEX idx_coin_article(article_id,action,effective,user_id),
  INDEX idx_coin_author(author_id,action,effective)
) ENGINE=InnoDB;

CREATE INDEX idx_session_login_day ON auth_session(user_id,created_at);
ALTER TABLE business_audit
  DROP CHECK ck_audit_action,
  DROP CHECK ck_audit_object,
  ADD CONSTRAINT ck_audit_action CHECK(action IN ('PROFILE_UPDATE','CONTACT_UPDATE','ARTICLE_PUBLISH','ARTICLE_UNPUBLISH','ARTICLE_RESTORE','ARTICLE_DELETE','COMMENT_CREATE','COMMENT_DELETE','LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL','READING_START','LOGIN_REWARD','READING_REWARD','ARTICLE_COIN')),
  ADD CONSTRAINT ck_audit_object CHECK(object_type IN ('PROFILE','CONTACT','ARTICLE','COMMENT','LIKE','FAVORITE','READING','WALLET'));
