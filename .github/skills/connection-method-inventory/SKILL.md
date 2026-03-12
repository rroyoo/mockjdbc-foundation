---
name: connection-method-inventory
description: Inventory java.sql.Connection methods, detect delegation gaps, and prioritize implementation order.
---

# connection-method-inventory

## When to use
Use this skill when you need a coverage map of `Connection` methods before adding ByteBuddy delegations.

## Actions
- List methods already delegated in `ConnectionFactory`.
- Detect missing methods and classify by behavior: `void`, primitive, object, stateful.
- Propose implementation order based on test impact and risk.

## Expected output
- Method matrix with status: implemented / missing / partial.
- Ordered backlog of methods to implement.
