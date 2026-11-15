ALTER TABLE governance_event
  ADD case_id BIGINT NULL,
  ADD case_revision BIGINT NULL,
  ADD CONSTRAINT fk_governance_case FOREIGN KEY (case_id) REFERENCES community_case(id),
  ADD INDEX idx_governance_case_time (case_id, occurred_at, id),
  ADD CONSTRAINT ck_governance_case_link CHECK (
    (case_id IS NULL AND case_revision IS NULL)
    OR (case_id IS NOT NULL AND case_revision IS NOT NULL AND case_revision > 0
        AND target_type IN ('ARTICLE','COMMENT') AND action IN ('HIDE','RESTORE'))
  );

ALTER TABLE contribution_invalidation
  ADD case_revision BIGINT NULL,
  ADD CONSTRAINT ck_contribution_case_revision CHECK (case_revision IS NULL OR case_revision>0);
