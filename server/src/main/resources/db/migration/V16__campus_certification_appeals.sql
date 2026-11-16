ALTER TABLE community_appeal
  MODIFY restriction_id BIGINT NULL,
  MODIFY restriction_revision BIGINT NULL,
  ADD certification_revision BIGINT NULL,
  ADD UNIQUE KEY uq_appeal_cert_revision(user_id, certification_revision),
  ADD CONSTRAINT ck_appeal_subject CHECK (
    (certification_revision IS NULL AND restriction_id IS NOT NULL
       AND restriction_revision IS NOT NULL AND restriction_revision > 0)
    OR (certification_revision IS NOT NULL AND certification_revision > 0
       AND restriction_id IS NULL AND restriction_revision IS NULL)
  );
