---
name: connection-state-semantics
description: Define deterministic state behavior for java.sql.Connection delegated methods, distributed across focused handler classes.
---

# connection-state-semantics

## When to use
Use this skill when implementing stateful `Connection` methods that must preserve and expose internal state.

## Design rule: state belongs in the handler that owns it
- Do **not** centralise all state in a single class (e.g. `ConnectionStateHandler`).
- Each feature handler owns and encapsulates its own state fields.
- State is always per-instance (never static) to guarantee test isolation.

## State ownership by handler
| Handler | State fields | JDBC default |
|---|---|---|
| `LifecycleHandler` | `closed: AtomicBoolean` | `false` |
| `TransactionHandler` | `autoCommit: AtomicBoolean` | `true` |
| `ConfigHandler` | `readOnly`, `transactionIsolation`, `holdability`, `catalog`, `schema`, `networkTimeout` | spec values |
| `WarningsHandler` | `warnings: AtomicReference<SQLWarning>` | `null` |
| `ClientInfoHandler` | `clientInfo: ConcurrentHashMap<String,String>` | empty |

## Actions
- Place new state fields in the handler responsible for that feature.
- Ensure getters reflect last successful setter call.
- Use `AtomicReference`, `AtomicBoolean`, `AtomicInteger` or thread-safe collections for all state.
- Apply state transitions only within the owning handler.

## Expected output
- State contract per handler (fields + defaults).
- Confirmation that no state leaks across handler boundaries.
