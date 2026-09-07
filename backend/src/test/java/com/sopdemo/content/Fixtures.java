package com.sopdemo.content;

/** Shared valid fixture documents (spec.md section 4 shape). */
final class Fixtures {

    private Fixtures() {
    }

    static String validRefundDoc() {
        return """
                ---
                sop_id: BILL-001
                title: "Duplicate Charge Refund"
                owner_team: Payments
                domain: Billing
                intent: refund_duplicate_charge
                risk_level: medium
                max_autonomy: assist
                ---

                ## Intent (When to use)
                - A customer reports the same charge appeared twice for one order
                - The customer asks for a refund of the duplicate charge

                ## Do Not Use When
                - The customer disputes the original fee
                - The account is under a chargeback

                ## Inputs Required
                ```yaml
                - name: refund_amount
                  type: number
                - name: customer_verified
                  type: boolean
                ```

                ## Eligibility Rules
                ```yaml
                - id: dup_refund_ok
                  conditions:
                    - input: refund_amount
                      op: lte
                      value: 150
                    - input: customer_verified
                      op: eq
                      value: true
                  action_ids:
                    - a_refund
                - id: needs_human
                  conditions:
                    - input: refund_amount
                      op: gt
                      value: 150
                  action_ids:
                    - a_human
                ```

                ## Actions
                ```yaml
                - id: a_refund
                  kind: refund
                  description: "Refund the duplicate charge."
                  max_amount: 150
                - id: a_escalate
                  kind: escalate
                  description: "Escalate to the senior billing queue."
                - id: a_human
                  kind: human_assist
                  description: "Route to a billing specialist."
                ```

                ## Boundaries
                ```yaml
                escalation:
                  - action_id: a_refund
                    input: refund_amount
                    op: gt
                    amount: 150
                    target_action_id: a_escalate
                ```

                ## Customer Messages
                ```yaml
                primary: "We spotted the duplicate charge and refunded it."
                escalation: "A billing specialist is reviewing your charges."
                ```
                """;
    }

    static String validAnswerDoc() {
        return """
                ---
                sop_id: SUP-ANSWER
                title: "Answer a Support Question"
                owner_team: Support
                domain: Support
                intent: answer_question
                risk_level: low
                max_autonomy: assist
                ---

                ## Intent (When to use)
                - The customer asks how to read their statement

                ## Do Not Use When
                - The customer is disputing accuracy of a charge

                ## Inputs Required
                ```yaml
                - name: topic
                  type: number
                ```

                ## Eligibility Rules
                ```yaml
                - id: answer_ok
                  conditions:
                    - input: topic
                      op: eq
                      value: 1
                  action_ids:
                    - a_answer
                ```

                ## Actions
                ```yaml
                - id: a_answer
                  kind: human_assist
                  description: "Provide a clear answer from the help center."
                ```

                ## Boundaries
                ```yaml
                escalation: []
                ```

                ## Customer Messages
                ```yaml
                primary: "Here is what you asked about."
                escalation: "A specialist can go deeper if you want."
                ```
                """;
    }
}
