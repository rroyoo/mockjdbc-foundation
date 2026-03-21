# Module Status Matrix

## Purpose

This matrix provides the current status of each project area and the next milestone expected by repository stewardship.

Status labels:
- `active`: implemented and used as part of the current baseline.
- `partial`: implemented in part; some contract or coverage gaps remain.
- `placeholder`: declared/scaffolded but without production implementation.
- `experimental`: not yet stable as a supported path.
- `deferred`: intentionally postponed with tracking reference.

## Current matrix

| Module/Project | Status | Evidence | Next milestone |
|---|---|---|---|
| `mockjdbc/mockjdbc-mock` | `partial` | Factories and handlers implemented with tests in `mockjdbc-mock/src/test/java` | Close remaining JDBC interface gaps (`Connection` + wrapper methods) |
| `mockjdbc/mockjdbc-proto` | `active` | Protobuf schemas and generated stubs in `mockjdbc-proto` | Keep contract stable and versioned with mock module needs |
| `mockjdbc/mockjdbc-proxy` | `placeholder`, `experimental` | No production source tree under `mockjdbc/mockjdbc-proxy/src/main/java` | Add first production classes and tests, or mark deferred/remove from active reactor path |
| `mockjdbc-spring-users` | `partial` | Independent app with tests for `h2` and `mockjdbc` profiles | Replace or reduce `systemPath` dependency coupling |

## Decision notes

- `mockjdbc-proxy` is explicitly treated as an experimental placeholder retained in the reactor.
- Its status must remain explicit in docs until it has production code and tests.

## Maintenance rule

Update this file in the same change set whenever a module changes status, scope, or target milestone.

