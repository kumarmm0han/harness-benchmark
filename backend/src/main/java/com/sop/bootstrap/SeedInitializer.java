package com.sop.bootstrap;

import com.sop.domain.Draft;
import com.sop.domain.DraftRepository;
import com.sop.service.SeedTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Idempotent demo seed (DR-003 / DES-010, AC-E2E-005):
 * inserts the spec §3 template as a saved draft only when the drafts table is
 * empty. Never duplicates records or overwrites user edits on restart.
 */
@Component
@Profile("demo")
public class SeedInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SeedInitializer.class);

  private final DraftRepository drafts;

  public SeedInitializer(DraftRepository drafts) {
    this.drafts = drafts;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (drafts.count() > 0) {
      log.info("seed skipped: drafts table already has {}", drafts.count(), "row(s)");
      return;
    }
    Draft d = new Draft();
    d.setSopId(SeedTemplate.SOP_ID);
    d.setSource(SeedTemplate.BILLING_REFUND);
    d.setRevision(1);
    d.setCreatedAt(Instant.now());
    d.setUpdatedAt(Instant.now());
    drafts.save(d);
    log.info("seed inserted draft {} revision 1", SeedTemplate.SOP_ID);
  }
}
