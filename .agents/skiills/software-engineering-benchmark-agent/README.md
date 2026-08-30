# Software Engineering Benchmark Agent

A harness-neutral skill for comparing OpenCode, Pi, Qwen Code, or other coding harnesses while keeping the model, requirements, principles, repository, and inference settings constant.

## Benchmark shape

```text
                 same Qwen model
                      |
        +-------------+-------------+
        |             |             |
     OpenCode         Pi         Qwen Code
        |             |             |
        +-------------+-------------+
                      |
                 compare outcomes
```

Each harness receives the same `PRINCIPLES.md`, `REQUIREMENTS.md`, starter repository, model endpoint, and inference parameters.

Each independently creates `ARCHITECTURE.md`, `TECHNICAL_DESIGN.md`, `TASKS.md`, implementation, tests, and `VERIFICATION.md`.

Recommended: at least three fresh runs per harness from the same starter commit. Evaluate software quality separately from harness efficiency.
