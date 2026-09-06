package com.sop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "drafts")
public class Draft {
  @Id
  @Column(name = "sop_id")
  private String sopId;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "revision", nullable = false)
  private long revision;

  @Column(name = "publish_failed_at")
  private Instant publishFailedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String sopId() { return sopId; }
  public void setSopId(String sopId) { this.sopId = sopId; }

  public String source() { return source; }
  public void setSource(String source) { this.source = source; }

  public long revision() { return revision; }
  public void setRevision(long revision) { this.revision = revision; }

  public Instant publishFailedAt() { return publishFailedAt; }
  public void setPublishFailedAt(Instant publishFailedAt) { this.publishFailedAt = publishFailedAt; }

  public Instant createdAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant updatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
