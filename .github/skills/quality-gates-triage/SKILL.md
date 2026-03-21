---
name: quality-gates-triage
description: Verify the relevant build, test, and CI signals for the touched scope, and record deltas rather than assumptions.
---

# quality-gates-triage

## When to use
Use this skill before closing a task that changes repository structure, build wiring, implementation code, or documentation that affects onboarding and maintenance.

## Actions
- Run the smallest relevant verification first: module tests, sample-app tests, or targeted smoke checks.
- Record pass/fail results as deltas, not generic claims.
- Check whether CI workflows exist for the current scope; if not, record that as a governance gap.
- Distinguish passing local checks from missing automation.
- Feed any failures or missing gates back into the roadmap checklist.

## Expected output
- A compact quality-gates summary.
- A note on missing or weak automation.
- Follow-up checklist items when verification is incomplete.

