package com.sopdemo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional draft operations (DES-008, FR-010, DR-001).
 * Saving is a single atomic upsert that increments the server-assigned revision
 * and clears the publication-failure indicator (FR-045). Saving does not and cannot
 * touch the publications table (ARC-005).
 */
@Service
public class DraftStore {

    private final DraftRepository repo;

    public DraftStore(DraftRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Draft save(String sopId, String source) {
        return repo.upsert(sopId, source);
    }

    public Optional<Draft> find(String sopId) {
        return repo.find(sopId);
    }

    public List<Draft> list() {
        return repo.list();
    }

    @Transactional
    public void setPublicationFailed(String sopId, boolean failed) {
        repo.setPublicationFailed(sopId, failed);
    }
}
