---
name: documentation-consistency
description: Keep ADRs, README files, agent docs, and implementation terminology aligned with the actual codebase.
---

# documentation-consistency

## When to use
Use this skill when repository documentation may no longer match current implementation, module scope, or agent workflows.

## Actions
- Compare documentation claims against source code, tests, and packaging structure.
- Replace stale class names, flows, or module descriptions with verified ones.
- Keep ADRs, agent instructions, and README files mutually consistent.
- Prefer explicit status notes over aspirational wording for incomplete areas.
- Update docs whenever architecture or ownership decisions change.

## Expected output
- A list of drift items found and corrected.
- Updated docs that reflect the real implementation state.
- Notes on any intentionally deferred documentation gaps.

