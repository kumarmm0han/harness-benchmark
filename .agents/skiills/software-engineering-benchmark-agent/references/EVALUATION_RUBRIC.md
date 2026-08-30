# Benchmark Evaluation Rubric

Recommended total: 100 points.

| Dimension | Points |
|---|---:|
| Functional correctness | 25 |
| Requirement adherence | 15 |
| Principle / guardrail adherence | 15 |
| Architecture quality | 10 |
| Technical design quality | 10 |
| Task decomposition | 5 |
| Simplicity | 10 |
| Test quality | 5 |
| Design-to-code consistency | 5 |

## Simplicity signals
Consider unnecessary classes, interfaces, dependencies, layers, duplicated code, cyclomatic complexity, and boilerplate. Do not use LOC alone.

## Separate harness-efficiency metrics
Keep these separate from software quality unless you intentionally define a combined score:
- wall-clock completion time
- input/output/total tokens
- tool calls
- commands executed
- failed builds
- test reruns
- files created/modified
- human interventions
- recovery loops
