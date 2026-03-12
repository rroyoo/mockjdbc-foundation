---
name: model-selection-policy
description: Select model strategy by task complexity, using auto by default and escalating only when needed.
---

# model-selection-policy

## When to use
Use this skill at the start of a task to choose a model strategy aligned with complexity and risk.

## Actions
- Start with `auto` by default.
- Classify complexity before coding:
  - `low`: small localized edit, single file, low behavioral risk.
  - `medium`: multi-method change, 2-3 files, moderate test impact.
  - `high`: cross-module refactor, public API impact, ambiguous requirements, or high regression risk.
- Select model strategy:
  - `low` -> keep `auto`.
  - `medium` -> keep `auto`; escalate only if progress stalls or ambiguity remains after first pass.
  - `high` -> use the most capable model available in the environment; if unavailable, keep `auto` and split into smaller validated steps.
- Re-evaluate the model choice when scope changes during implementation.

## Expected output
- Chosen model strategy with short rationale.
- Complexity tier and escalation trigger notes.

