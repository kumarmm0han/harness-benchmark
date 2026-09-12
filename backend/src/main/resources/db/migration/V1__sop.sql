CREATE TABLE drafts (
  sop_id varchar(64) PRIMARY KEY CHECK (sop_id ~ '^[A-Z][A-Z0-9-]{0,63}$'),
  source text NOT NULL CHECK (octet_length(source) <= 65536),
  revision bigint NOT NULL CHECK (revision > 0),
  failed_revision bigint,
  current_version integer,
  CHECK (failed_revision IS NULL OR failed_revision = revision)
);
CREATE TABLE publications (
  sop_id varchar(64) NOT NULL REFERENCES drafts(sop_id),
  version integer NOT NULL CHECK (version > 0),
  draft_revision bigint NOT NULL CHECK (draft_revision > 0),
  source text NOT NULL,
  snapshot jsonb NOT NULL,
  PRIMARY KEY (sop_id, version),
  UNIQUE (sop_id, draft_revision)
);
ALTER TABLE drafts ADD CONSTRAINT current_publication FOREIGN KEY (sop_id, current_version) REFERENCES publications(sop_id, version);
CREATE FUNCTION immutable_publication() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Published snapshots are immutable';
END;
$$;
CREATE TRIGGER publication_immutable BEFORE UPDATE OR DELETE ON publications FOR EACH ROW EXECUTE FUNCTION immutable_publication();
