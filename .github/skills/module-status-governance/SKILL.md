---
name: module-status-governance
description: Maintain an explicit status and decision model for each repository module, including active, partial, placeholder, deferred, or experimental states.
---

# module-status-governance

## When to use
Use this skill when deciding what a module currently is, what it is allowed to contain, and what the next milestone should be.

## Actions
- Build a status matrix for each module: purpose, current implementation level, known gaps, next milestone.
- Mark modules explicitly as `active`, `partial`, `placeholder`, `deferred`, or `experimental`.
- Flag mismatches between README claims, POM declarations, and actual source layout.
- Record whether a module should be completed, documented as partial, or removed from the active path.
- Keep standalone sample applications and reactor modules clearly distinguished.

## Expected output
- A module status table with decisions and rationale.
- A short list of module-level follow-up actions.
- Explicit notes on any doc/code drift affecting module expectations.

