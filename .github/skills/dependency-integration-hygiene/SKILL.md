---
name: dependency-integration-hygiene
description: Keep module and sample-app dependency wiring reproducible, explicit, and aligned with the intended packaging model.
---

# dependency-integration-hygiene

## When to use
Use this skill when reviewing Maven dependency wiring, local artifact usage, sample-app integration, and cross-project build assumptions.

## Actions
- Inspect parent and child POMs for fragile coupling, especially `systemPath`, local target-jar assumptions, and duplicated version management.
- Distinguish between intentional independence and accidental coupling.
- Document the current bootstrap path when a stronger packaging model is not yet implemented.
- Prefer reproducible consumption paths such as normal Maven coordinates, local install/publish steps, or documented bootstrap commands.
- Capture integration risks that affect CI, onboarding, or repeatable local runs.

## Expected output
- A dependency/integration hygiene assessment.
- Recommended packaging path and any interim constraints.
- Checklist items for improving reproducibility.

