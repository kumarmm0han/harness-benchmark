-- V1__schema.sql — SOP demo (DES-002 / DR-001).
-- Migrations from an empty database. Consecutive integer versions per sop_id.

CREATE TABLE IF NOT EXISTS drafts (
  sop_id              text        PRIMARY KEY,
  source              text        NOT NULL,
  revision            bigint      NOT NULL CHECK (revision >= 1),
  publish_failed_at   timestamptz,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS publications (
  sop_id          text           NOT NULL,
  version         integer        NOT NULL CHECK (version >= 1),
  draft_revision  bigint         NOT NULL,
  source          text           NOT NULL,
  content_json    text           NOT NULL,
  envelope_json   text           NOT NULL,
  published_at    timestamptz    NOT NULL,
  PRIMARY KEY (sop_id, version),
  CONSTRAINT uq_publications_draft_revision UNIQUE (sop_id, draft_revision)
);

CREATE INDEX IF NOT EXISTS ix_publications_sopid ON publications (sop_id);

CREATE TABLE IF NOT EXISTS sop_current (
  sop_id          text        PRIMARY KEY,
  version         integer     NOT NULL,
  published_at    timestamptz NOT NULL,
  CONSTRAINT fk_sop_current_publication FOREIGN KEY (sop_id, version)
    REFERENCES publications(sop_id, version) ON DELETE CASCADE
);
