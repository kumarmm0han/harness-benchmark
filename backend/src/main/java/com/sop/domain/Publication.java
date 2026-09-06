package com.sop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "publications")
@IdClass(Publication.Pk.class)
public class Publication {

  @Id
  @Column(name = "sop_id")
  private String sopId;

  @Id
  @Column(name = "version")
  private int version;

  @Column(name = "draft_revision", nullable = false)
  private long draftRevision;

  @Column(name = "source", nullable = false, columnDefinition = "text")
  private String source;

  @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
  private String contentJson;   // canonical content, serialized

  @Column(name = "envelope_json", nullable = false, columnDefinition = "jsonb")
  private String envelopeJson;  // {sop_id, version, published_at, content}

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  public String sopId() { return sopId; }
  public void setSopId(String sopId) { this.sopId = sopId; }

  public int version() { return version; }
  public void setVersion(int version) { this.version = version; }

  public long draftRevision() { return draftRevision; }
  public void setDraftRevision(long draftRevision) { this.draftRevision = draftRevision; }

  public String source() { return source; }
  public void setSource(String source) { this.source = source; }

  public String contentJson() { return contentJson; }
  public void setContentJson(String contentJson) { this.contentJson = contentJson; }

  public String envelopeJson() { return envelopeJson; }
  public void setEnvelopeJson(String envelopeJson) { this.envelopeJson = envelopeJson; }

  public Instant publishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

  public static class Pk implements Serializable {
    private String sopId;
    private int version;
    public Pk() {}
    public Pk(String sopId, int version) { this.sopId = sopId; this.version = version; }
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Pk other)) return false;
      if (version != other.version) return false;
      return sopId != null ? sopId.equals(other.sopId) : other.sopId == null;
    }
    @Override public int hashCode() { return 31 * (sopId == null ? 0 : sopId.hashCode()) + version; }
  }
}
