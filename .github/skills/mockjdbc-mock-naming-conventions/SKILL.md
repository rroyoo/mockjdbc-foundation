---
name: mockjdbc-mock-naming-conventions
description: Apply mockjdbc-mock naming conventions for handlers, factories, test methods, and display names.
---

# mockjdbc-mock-naming-conventions

## When to use
Use this skill when adding or renaming production/test code in `mockjdbc/mockjdbc-mock`.

## Actions
- Use `*Factory` for ByteBuddy wiring/assembly classes (`ConnectionFactory`, `StatementFactory`).
- Use `*Handler` suffix for focused behavior/state units (`LifecycleHandler`, `StatementQueryHandler`, etc.).
- Keep handler names feature-specific (`Config`, `Warnings`, `ExecutionState`, `GeneratedKeys`) instead of generic catch-all names.
- Keep method names behavior-first and explicit (`createStatement`, `executeQuery`, `setAutoCommit`).
- Name test methods with `should...` and match `@DisplayName` in Given-When-Then style.
- Keep domain naming stable between transport and JDBC layers (`resultSet`, `updateCount`, `generatedKeys`, `parameters`).

## Anti-patterns to avoid
- Avoid generic suffixes like `Util`, `Helper`, `Manager`, `Service` when the class is actually a handler or factory.
- Avoid catch-all names such as `CommonHandler`, `GenericHandler`, or `JdbcHandler` that hide responsibility.
- Avoid vague boolean/method names like `doIt`, `process`, `handle` without feature context.
- Avoid test names that describe implementation details instead of observable JDBC behavior.

## Expected output
- New symbols that follow existing `mockjdbc-mock` naming style.
- Consistent naming across factory wiring, handler responsibilities, and tests.
- No ambiguous or overly generic class/method names.
