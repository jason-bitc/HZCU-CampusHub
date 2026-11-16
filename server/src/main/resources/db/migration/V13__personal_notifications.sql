CREATE TABLE personal_notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_id BIGINT NOT NULL,
  kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id BIGINT NOT NULL,
  source_revision BIGINT NOT NULL,
  outcome VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at DATETIME(6) NOT NULL,
  read_at DATETIME(6) NULL,
  FOREIGN KEY(recipient_id) REFERENCES platform_user(id) ON DELETE CASCADE,
  UNIQUE KEY uq_notification_source(recipient_id,kind,source_id,source_revision),
  INDEX idx_notification_inbox(recipient_id,id DESC),
  INDEX idx_notification_unread(recipient_id,read_at),
  CHECK(kind IN ('CAMPUS_VERIFIED','COMMENT_REPLY','CASE_DECISION','ARTICLE_MODERATION',
                'COMMENT_MODERATION','RESTRICTION_CHANGED','APPEAL_DECIDED','CONTRIBUTION_INVALIDATED')),
  CHECK(source_id>0 AND source_revision>0)
) ENGINE=InnoDB;
