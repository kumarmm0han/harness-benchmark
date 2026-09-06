package com.sop.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication, Publication.Pk> {

  Optional<Publication> findBySopIdAndVersion(String sopId, int version);

  Optional<Publication> findBySopIdAndDraftRevision(String sopId, long draftRevision);

  /**
   * Next version for a SOP: COALESCE(MAX(version),0)+1.
   * Read under the advisory lock (DES-008b) so a concurrent publisher cannot
   * race for the same integer.
   */
  @Query("select coalesce(max(p.version), 0) + 1 from Publication p where p.sopId = :sopId")
  int nextVersionFor(@Param("sopId") String sopId);
}
