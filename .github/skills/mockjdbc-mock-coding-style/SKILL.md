---
name: mockjdbc-mock-coding-style
description: Apply the coding style used in mockjdbc-mock handlers, factories, and driver classes.
---

# mockjdbc-mock-coding-style

## When to use
Use this skill when changing production code under `mockjdbc/mockjdbc-mock/src/main/java`.

## Actions
- Keep classes single-purpose and feature-scoped (factory wiring, handler state, transport client, converter).
- Prefer `final` classes where extension is not required and small, explicit constructors.
- Prefer local type inference (`var`) for local variables when the inferred type is obvious from the right-hand side.
- Validate mandatory inputs early and fail fast with `IllegalArgumentException`.
- Keep method behavior deterministic and explicit (no hidden fallback paths).
- Wrap lower-level failures into JDBC-oriented exceptions/messages (`SQLException` for JDBC flows).
- In ByteBuddy wiring, use explicit matchers (`named`, `takesArguments`, `takesNoArguments`) and avoid broad interception.
- Preserve existing naming/structure conventions: handler-per-feature, factory methods grouped by JDBC API family, and minimal side effects.

## Expected output
- Focused production changes aligned with existing handler/factory architecture.
- Clear exception paths and deterministic method results.
- No unrelated refactors or style drift from `mockjdbc-mock` conventions.
