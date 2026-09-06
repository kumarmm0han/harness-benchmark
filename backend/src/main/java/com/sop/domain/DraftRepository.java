package com.sop.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, String> {

  @Query("select count(d) from Draft d")
  long countDrafts();

  List<Draft> findAllByOrderBySopIdAsc();
}
