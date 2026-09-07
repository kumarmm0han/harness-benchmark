package com.sopdemo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.sopdemo.content.ContentEngine;
import com.sopdemo.content.Issue;

/**
 * Deterministic demo seed (DR-003, DES-208): with the {@code demo} profile the
 * duplicate-charge example is saved as a draft (sop_id {@code BILL-REFUND-001}).
 * Idempotent and edit-safe: the conditional insert only fills a missing draft
 * and never overwrites an existing one (repeated startups neither duplicate
 * records nor clobber user edits). Runs after Flyway has migrated the schema.
 */
@Component
@Profile("demo")
public class SeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedInitializer.class);
    public static final String DEMO_SOP_ID = "BILL-REFUND-001";

    private final JdbcTemplate jdbc;
    private final ContentEngine engine;

    public SeedInitializer(JdbcTemplate jdbc, ContentEngine engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        String source = loadTemplate();
        Integer inserted = jdbc.update(
                "INSERT INTO drafts (sop_id, source, revision) "
                        + "SELECT ?, ?::text, 1 WHERE NOT EXISTS (SELECT 1 FROM drafts WHERE sop_id = ?)",
                DEMO_SOP_ID, source, DEMO_SOP_ID);
        if (inserted != null && inserted > 0) {
            log.info("Seeded demo draft {} (revision 1) from seed/duplicate-charge.md", DEMO_SOP_ID);
            if (!engine.analyze(source).valid()) {
                String codes = engine.analyze(source).issues().stream().map(Issue::code).toList().toString();
                log.warn("Seeded draft {} not currently publishable: {}", DEMO_SOP_ID, codes);
            }
        } else {
            log.info("Demo draft {} already present; leaving existing draft untouched", DEMO_SOP_ID);
        }
    }

    private static String loadTemplate() {
        try (var in = new ClassPathResource("seed/duplicate-charge.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("seed template missing", e);
        }
    }
}
