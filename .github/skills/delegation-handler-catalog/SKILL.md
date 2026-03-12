---
name: delegation-handler-catalog
description: Map JDBC method groups to reusable delegation handlers.
---

# delegation-handler-catalog

## When to use
Use this skill when deciding whether to reuse existing handlers or create new ones for delegated methods.

## Actions
- Group methods by return style and state requirements.
- Reuse existing handlers where possible.
- Add new handlers only when behavior differs meaningfully.
- Keep handlers small and focused.

## Expected output
- Handler mapping table: method group -> handler class.
- List of new handlers to add (if any).
