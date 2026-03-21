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
| `mockjdbc/mockjdbc-proto` | `active` | Protobuf schemas and generated stubs in `mockjdbc-proto` | Keep contract stable and versioned with mock/proxy/wiremock module needs |
| `mockjdbc/mockjdbc-proxy` | `partial`, `active` | Production classes and tests under `mockjdbc-proxy/src/main/java` and `mockjdbc-proxy/src/test/java` | Complete Docker-backed Kafka integration validation in CI and finalize ADR-0002 checklist |
| `mockjdbc/mockjdbc-wiremock` | `partial`, `active` | Bridge classes + tests in `mockjdbc-wiremock/src/main/java` and `mockjdbc-wiremock/src/test/java` | Validate Testcontainers integration in Docker-capable environment and finalize ADR-0004 checklist |
| `mockjdbc-spring-users` | `partial` | Independent app with tests for `h2` and `mockjdbc` profiles | Replace or reduce `systemPath` dependency coupling |

## Decision notes

- `mockjdbc-proxy` is no longer a pure placeholder; it is now partial/active and tracked by ADR-0002.
- `mockjdbc-wiremock` is introduced as a new partial/active bridge module tracked by ADR-0004.

## Maintenance rule

Update this file in the same change set whenever a module changes status, scope, or target milestone.
