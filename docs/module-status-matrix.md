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
| `mockjdbc/mockjdbc-mock` | `partial` | Factories and handlers implemented with tests in `mockjdbc-mock/src/test/java` | Phase 1: close statement-family contract; Phase 2: settle result/value fidelity |
| `mockjdbc/mockjdbc-proto` | `active` | Protobuf schemas, generated stubs, and Buf `FILE` compatibility gate | Phase 2: evolve contracts only for explicitly supported fidelity work |
| `mockjdbc/mockjdbc-proxy` | `partial`, `active` | Production classes and tests under `mockjdbc-proxy/src/main/java` and `mockjdbc-proxy/src/test/java`; Docker-backed Kafka integration passes in CI (ADR-0005) | Phase 3: define and enforce sensitive-data masking before Kafka publication |
| `mockjdbc/mockjdbc-wiremock` | `partial`, `active` | Bridge classes + tests in `mockjdbc-wiremock/src/main/java` and `mockjdbc-wiremock/src/test/java`; Docker-backed Kafka integration passes in CI (ADR-0005) | Phase 3: bound registration retries and add a dead-letter path |
| `mockjdbc-spring-users` | `partial` | Independent app with tests for `h2` and `mockjdbc` profiles; mock-profile E2E requires Docker and is exercised in CI | Phase 4: keep clean reactor-to-sample bootstrap and Docker-backed CI coverage documented |

## Decision notes

- `mockjdbc-proxy` is no longer a pure placeholder; it is now partial/active and tracked by ADR-0002.
- `mockjdbc-wiremock` is introduced as a new partial/active bridge module tracked by ADR-0004.

## Maintenance rule

Update this file in the same change set whenever a module changes status, scope, or target milestone.
