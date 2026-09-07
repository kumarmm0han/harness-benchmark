-- V1: initial schema (DR-001, FR-042/043/045, PRN-006)

-- Editable draft: one per SOP. Server-owned revision; failure indicator (FR-010, FR-045).
CREATE TABLE drafts (
    sop_id          text PRIMARY KEY,
    source          text NOT NULL,
    revision        bigint NOT NULL CHECK (revision > 0),
    publish_failed  boolean NOT NULL DEFAULT false,
    saved_at        timestamptz NOT NULL DEFAULT now()
);

-- Single current pointer per SOP (FR-042/045).
CREATE TABLE sops (
    sop_id          text PRIMARY KEY,
    current_version int NOT NULL CHECK (current_version > 0)
);

-- Immutable published snapshots (PRN-001, FR-042/043).
-- UNIQUE (sop_id, source_revision) enforces "one publication per exact saved draft revision" (FR-042).
CREATE TABLE sop_versions (
    sop_id          text NOT NULL,
    version         int NOT NULL CHECK (version > 0),
    source          text NOT NULL,
    canonical       jsonb NOT NULL,
    source_revision bigint NOT NULL,
    published_at    timestamptz NOT NULL,
    PRIMARY KEY (sop_id, version),
    UNIQUE (sop_id, source_revision)
);
