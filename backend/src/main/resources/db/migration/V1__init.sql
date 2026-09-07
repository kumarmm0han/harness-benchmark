-- V1__init.sql — SOP demo schema (DR-001, PRN-006)
-- Enforced database constraints: unique (sop_id, version) and unique (sop_id, draft_revision).

CREATE TABLE sop_draft (
    sop_id              TEXT PRIMARY KEY,
    source              TEXT NOT NULL,
    revision            BIGINT NOT NULL CHECK (revision > 0),
    publication_failed  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sop_publication (
    id              BIGSERIAL PRIMARY KEY,
    sop_id          TEXT NOT NULL,
    version         INTEGER NOT NULL CHECK (version > 0),
    draft_revision  BIGINT  NOT NULL,
    source          TEXT NOT NULL,
    snapshot        JSONB NOT NULL,
    published_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (sop_id, version),
    UNIQUE (sop_id, draft_revision)
);

CREATE TABLE sop_current (
    sop_id   TEXT PRIMARY KEY,
    version  INTEGER NOT NULL CHECK (version > 0)
);

COMMENT ON TABLE sop_publication IS 'Immutable canonical snapshots per (sop_id, version).';
COMMENT ON TABLE sop_current     IS 'The single current-version pointer per sop_id.';
