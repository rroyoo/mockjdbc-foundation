---
name: delegation-handler-catalog
description: Map JDBC method groups to focused, feature-scoped handler classes.
---

# delegation-handler-catalog

## When to use
Use this skill when deciding how to structure handler classes for delegated methods.

## Design rule: one handler per feature
- Each handler class covers **one cohesive feature group**, not a mix of unrelated methods.
- Never grow a single handler into a catch-all class.
- A handler is too large if it holds state or methods for more than one concern.

## Feature groups (reference)
| Handler class | Responsibility |
|---|---|
| `LifecycleHandler` | `close`, `isClosed` |
| `TransactionHandler` | `commit`, `rollback`, `setAutoCommit`, `getAutoCommit` |
| `ConfigHandler` | `setReadOnly`, `isReadOnly`, `setCatalog`, `getCatalog`, `setSchema`, `getSchema`, `setHoldability`, `getHoldability`, `setTransactionIsolation`, `getTransactionIsolation`, `setNetworkTimeout`, `getNetworkTimeout` |
| `WarningsHandler` | `getWarnings`, `clearWarnings` |
| `SavepointHandler` | `setSavepoint`, `releaseSavepoint` |
| `ClientInfoHandler` | `setClientInfo`, `getClientInfo` |
| `StatementHandler` | `createStatement`, `prepareStatement`, `prepareCall` |
| `MetadataHandler` | `getMetaData`, `nativeSQL`, `getTypeMap`, `setTypeMap` |
| `GenericVoidHandler` | void no-ops with no state |
| `NullResultHandler` | no-ops that must return null |

## Actions
- Identify the feature group for each new method.
- Create a new handler class if no existing handler fits that group.
- Reuse an existing handler only if it covers the same feature.
- Keep handlers small: if a handler exceeds ~5 methods, consider splitting.

## Expected output
- Feature group assignment for each method.
- List of new handler classes to create.
- List of existing handlers to extend (if appropriate).
