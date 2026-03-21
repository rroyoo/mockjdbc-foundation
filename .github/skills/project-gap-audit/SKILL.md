---
name: project-gap-audit
description: Verify repository gaps from code, tests, docs, and build evidence, then classify them by module and priority.
---

# project-gap-audit

## When to use
Use this skill when you need a current, evidence-based view of what is missing, partial, drifting, or blocked in the repository.

## Actions
- Audit the main areas: `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, `mockjdbc-spring-users`, docs, agent metadata, and CI.
- Classify findings using explicit statuses: `done`, `partial`, `open`, `deferred`, `unknown`.
- Back every gap with concrete evidence from source files, tests, POM files, or missing assets.
- Separate implementation gaps from packaging, documentation, and governance gaps.
- Feed the findings into the ADR checklist in priority order.

## Expected output
- A module-by-module gap inventory with evidence.
- A prioritized shortlist of next actions.
- Clear distinction between verified facts and inferred risks.

