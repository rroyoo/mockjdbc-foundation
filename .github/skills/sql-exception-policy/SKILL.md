---
name: sql-exception-policy
description: Apply a consistent SQLException policy for unsupported and invalid JDBC operations.
---

# sql-exception-policy

## When to use
Use this skill when defining failure behavior for delegated JDBC methods.

## Actions
- Use `SQLFeatureNotSupportedException` for intentionally unsupported JDBC features.
- Use `SQLException` for invalid state transitions and JDBC contract violations.
- Keep error messages short, clear, and assertion-friendly.

## Expected output
- Exception policy mapped by method group.
- Suggested assertions for test coverage.
