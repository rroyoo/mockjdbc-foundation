---
name: connection-factory-tests
description: Define tests for ConnectionFactory creation flow and delegation behavior.
---

# connection-factory-tests

## When to use
Use this skill when adding or updating tests around `ConnectionFactory` and delegated `Connection` behavior.

## Actions
- Add test for successful connection creation from valid `MockConfig`.
- Add test asserting expected exception when `MockConfig` is `null`.
- Add behavioral tests for delegated defaults/state when relevant.
- Keep test names behavior-oriented and concise.

## Expected output
- Test cases list and expected outcomes.
- Minimal updates to test classes.
