package com.sop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sop_current")
public class SopCurrent {
  @Id
  @Column(name = "sop_id")
  private String sopId;

  @Column(name = "version")
  private int version;

  @Column(name = "published_at")
  private Instant publishedAt;

  public String sopId() { return sopId; }
  public void setSopId(String sopId) { this.sopId = sopId; }

  public int version() { return version; }
  public void setVersion(int version) { this.version = version; }

  public Instant publishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
