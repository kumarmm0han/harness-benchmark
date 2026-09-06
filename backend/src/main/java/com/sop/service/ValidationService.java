package com.sop.service;

import com.sop.domain.Content;
import com.sop.domain.ParsedDocument;
import com.sop.domain.PipelineResult;
import com.sop.domain.ValidatorPipeline;
import com.sop.dto.Issue;
import org.springframework.stereotype.Service;

import java.util.List;

/** Thin orchestration over the pipeline (DES-005 / DES-009). Stateless. */
@Service
public class ValidationService {

  public record Outcome(boolean valid, List<Issue> issues, Content content) {}

  public Outcome validate(String source) {
    PipelineResult r = ValidatorPipeline.validate(source);
    if (!r.valid()) {
      return new Outcome(false, r.issues(), null);
    }
    ParsedDocument doc = r.parsedDocument();
    if (doc == null) {
      return new Outcome(false, List.of(), null);
    }
    Content content = ValidatorPipeline.buildContent(doc);
    return new Outcome(true, List.of(), content);
  }
}
