---
name: mockjdbc-mock-testing-style
description: Apply the test style used in mockjdbc-mock, including Given-When-Then DisplayName and behavior-focused assertions.
---

# mockjdbc-mock-testing-style

## When to use
Use this skill when creating or updating tests under `mockjdbc/mockjdbc-mock/src/test/java`.

## Actions
- Use JUnit 5 with `@DisplayName` in Given-When-Then style for every test.
- Name test methods with `should...` behavior-driven naming.
- Prefer local type inference (`var`) in tests for arrange/act variables when the type is evident.
- Keep tests behavior-focused: assert externally observable JDBC behavior, not internals.
- Follow Arrange/Act/Assert structure; add section comments when it improves readability.
- Prefer direct assertions from `org.junit.jupiter.api.Assertions` (`assertEquals`, `assertThrows`, `assertDoesNotThrow`, etc.).
- Cover defaults, state transitions, error paths, and contract semantics (query/update/result-set/generated-keys).
- Keep tests deterministic and isolated (`@BeforeEach`/`@AfterEach` for resources like gRPC servers).

## Expected output
- Readable tests consistent with existing mockjdbc-mock suite style.
- Display names and method names that document behavior clearly.
- Coverage of happy path and edge/error semantics without flaky timing assumptions.

