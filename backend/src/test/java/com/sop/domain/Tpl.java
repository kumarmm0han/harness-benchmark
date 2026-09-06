package com.sop.domain;

import com.sop.dto.Issue;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared valid template fixture (identical to the spec §3 seed and the UI
 * template insertion). Tests mutate it to build broken variants.
 */
public final class Tpl {
  private Tpl() {}

  public static String VALID = """
      ---
      sop_id: BILL-REFUND-001
      title: Refund for Duplicate Charge
      owner_team: Billing Operations
      domain: Billing
      intent: refund_duplicate_charge
      risk_level: medium
      max_autonomy: assist
      ---

      ## Intent (When to use)
      - Customer reports a duplicate charge.
      - A support representative confirms the duplicate.

      ## Do Not Use When
      - Fraud is suspected.

      ## Inputs Required
      ```yaml
      - name: refund_amount
        type: number
      - name: duplicate_confirmed
        type: boolean
      ```

      ## Eligibility Rules
      ```yaml
      - id: R1
        conditions:
          - input: duplicate_confirmed
            op: eq
            value: true
          - input: refund_amount
            op: lte
            value: 200
        action_ids: [A1]
      - id: R2
        conditions:
          - input: refund_amount
            op: gt
            value: 200
        action_ids: [A2]
      ```

      ## Actions
      ```yaml
      - id: A1
        kind: refund
        description: A representative may process a confirmed duplicate refund within the limit.
        max_amount: 200
      - id: A2
        kind: escalate
        description: Refer an over-limit request to Billing Support for review.
      ```

      ## Boundaries
      ```yaml
      escalation:
        - action_id: A1
          input: refund_amount
          op: gt
          amount: 200
          target_action_id: A2
      ```

      ## Customer Messages
      ```yaml
      primary: A representative can review the confirmed duplicate charge for a refund.
      escalation: This request needs additional review because it exceeds the refund limit.
      ```
      """;

  public static List<Issue> validate(String src) {
    return ValidatorPipeline.validate(src).issues();
  }

  public static boolean hasCode(List<Issue> issues, String code) {
    return issues.stream().anyMatch(i -> code.equals(i.code()));
  }

  public static String codes(List<Issue> issues) {
    return issues.stream().map(Issue::code).collect(Collectors.joining(","));
  }
}
