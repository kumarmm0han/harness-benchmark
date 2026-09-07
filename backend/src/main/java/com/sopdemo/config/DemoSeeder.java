package com.sopdemo.config;

import com.sopdemo.persistence.DraftRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Deterministic, idempotent seed (DR-003). Runs only when the explicit demo profile
 * enables it; uses {@code INSERT ... ON CONFLICT DO NOTHING} so it never duplicates or
 * overwrites user edits. Seeded as a SAVED DRAFT (not yet published) with the exact
 * template from spec.md §3.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private final DraftRepository drafts;
    private final DemoProperties props;

    public DemoSeeder(DraftRepository drafts, DemoProperties props) {
        this.drafts = drafts;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.seed() == null || !props.seed().enabled()) {
            return; // seed only on the explicit demo profile
        }
        String sopId = "BILL-REFUND-001";
        if (drafts.exists(sopId)) {
            return; // preserve user edits
        }
        String source = readFromClasspath("seed/duplicate-charge.md");
        drafts.upsert(sopId, source);
    }

    private static String readFromClasspath(String path) {
        ClassPathResource r = new ClassPathResource(path);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
            return in.lines().reduce("", (a, b) -> a + b + "\n").replaceFirst("\\Z\n$", "").stripTrailing();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read seed resource " + path, e);
        }
    }
}
