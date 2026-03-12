---
name: connection-state-semantics
description: Define deterministic state behavior for java.sql.Connection delegated methods.
---

# connection-state-semantics

## When to use
Use this skill when implementing stateful `Connection` methods that must preserve and expose internal state.

## Actions
- Define in-memory state model for key flags (`closed`, `autoCommit`, `readOnly`, transaction isolation).
- Ensure getters reflect last successful setter call.
- Apply state transitions for `close`, transaction methods, and validity checks.
- Keep behavior stable and predictable for tests.

## Expected output
- State contract for delegated methods.
- Required handler/state holder changes.
