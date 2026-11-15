ALTER TABLE category
  ADD canonical_id BIGINT NULL,
  ADD CONSTRAINT fk_category_canonical FOREIGN KEY (canonical_id) REFERENCES category(id),
  ADD CONSTRAINT ck_category_canonical CHECK (canonical_id IS NULL OR (canonical_id <> id AND enabled=FALSE));

ALTER TABLE governance_event
  ADD related_category_id BIGINT NULL;
