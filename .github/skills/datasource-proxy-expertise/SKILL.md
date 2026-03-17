---
name: datasource-proxy-expertise
description: Apply datasource-proxy patterns for JDBC query interception, logging safety, and deterministic listener behavior.
---

# datasource-proxy-expertise

## When to use
Use this skill when adding or modifying datasource-level query interception (`DataSource`, `Connection`, `Statement`) and listener wiring.

## Actions
- Define interception scope first (`beforeQuery`, `afterQuery`, error path) and keep it explicit.
- Capture SQL and parameters in a stable format that can be asserted in tests.
- Prefer local type inference (`var`) for local variables when it improves readability without hiding intent.
- Redact or avoid sensitive values (credentials, tokens, personal data) in logs and traces.
- Keep listener side effects isolated (no mutation of JDBC execution state unless explicitly required).
- Preserve deterministic behavior for timing, ordering, and exception propagation.

## Expected output
- A clear listener/interceptor strategy for datasource-proxy integration.
- Explicit capture/redaction rules for SQL and parameters.
- Test notes covering success, failure, and sensitive-data handling paths.

