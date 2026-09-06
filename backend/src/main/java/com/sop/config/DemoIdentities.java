package com.sop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configured demo identity (DES-007). Fixed local-only identities — not
 * production auth (FR-001). Values come from `app.demo.*`.
 */
@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoIdentities {
  public static final String AUTHOR = "demo-author";
  public static final String CONSUMER = "demo-consumer";

  private String author = AUTHOR;
  private String consumer = CONSUMER;

  public String author() { return author; }
  public void setAuthor(String author) { this.author = author == null || author.isBlank() ? AUTHOR : author; }

  public String consumer() { return consumer; }
  public void setConsumer(String consumer) { this.consumer = consumer == null || consumer.isBlank() ? CONSUMER : consumer; }

  public Set<String> all() {
    return Set.of(author(), consumer());
  }
}
