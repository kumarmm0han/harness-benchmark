package com.sopdemo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.config.DemoSeeder;
import com.sopdemo.persistence.Draft;
import com.sopdemo.persistence.DraftRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deterministic idempotent seed (DR-003, FR-001): the seeder (1) creates the demo draft when
 * absent and (2) never overwrites or duplicates an existing draft for that id. Runs against a
 * real PostgreSQL with {@code sopdemo.seed.enabled=true}.
 */
@SpringBootTest(properties = "sopdemo.seed.enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeedIT {

    private static final String SEED_SOP_ID = "BILL-REFUND-001";

    @Autowired
    DemoSeeder seeder;

    @Autowired
    DraftRepository drafts;

    @Test
    @Order(1)
    void seedsWhenAbsentAndIsIdempotentOnReRun() {
        // The ApplicationRunner ran once at context startup with seed enabled.
        Draft first = drafts.find(SEED_SOP_ID).orElseThrow();
        assertThat(first.source()).isNotBlank();
        long startingRevision = first.revision();

        // Explicit second run must be a no-op for the same row.
        seeder.run(null);

        Draft afterReRun = drafts.find(SEED_SOP_ID).orElseThrow();
        assertThat(afterReRun.source()).isEqualTo(first.source());
        assertThat(afterReRun.revision()).isEqualTo(startingRevision);
    }

    @Test
    @Order(2)
    void neverOverwritesAnExistingDraft() {
        // Simulate a user editing the seeded draft after seeding.
        String userEdit = "user-modified source that the seeder must not touch";
        drafts.upsert(SEED_SOP_ID, userEdit);
        long revisionAfterEdit = drafts.find(SEED_SOP_ID).orElseThrow().revision();

        // A further seeder run must preserve the user's content and revision.
        seeder.run(null);

        Draft preserved = drafts.find(SEED_SOP_ID).orElseThrow();
        assertThat(preserved.source()).isEqualTo(userEdit);
        assertThat(preserved.revision()).isEqualTo(revisionAfterEdit);
    }
}
