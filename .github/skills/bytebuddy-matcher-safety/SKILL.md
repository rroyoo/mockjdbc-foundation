---
name: bytebuddy-matcher-safety
description: Design ByteBuddy matchers that avoid ambiguous method delegation.
---

# bytebuddy-matcher-safety

## When to use
Use this skill when adding or modifying `MethodDelegation` rules for JDBC interfaces.

## Actions
- Use specific matchers (`named`, `takesArguments`, return type constraints when needed).
- Avoid broad `any()` matchers for interface-wide interception.
- Exclude `Object` methods unless explicitly required.
- Validate matcher combinations for ambiguity before adding more delegations.

## Expected output
- Concrete matcher rules ready to be used in builder chaining.
- Notes about ambiguity risks and applied mitigations.
