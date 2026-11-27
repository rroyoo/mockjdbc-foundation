# Module Status Matrix

## Purpose

This matrix provides the current status of each project area and the next milestone expected by repository stewardship.

The JDBC implementation backlog is paused while the public beta launch is prioritized in [`open-source-launch-roadmap.md`](open-source-launch-roadmap.md). The milestones below are candidates for when implementation work resumes, not current launch blockers.

Status labels:
- `active`: implemented and used as part of the current baseline.
- `partial`: implemented in part; some contract or coverage gaps remain.
- `placeholder`: declared/scaffolded but without production implementation.
- `experimental`: not yet stable as a supported path.
- `deferred`: intentionally postponed with tracking reference.

## Current matrix

| Module/Project | Status | Evidence | Next milestone |
|---|---|---|---|
| `mockjdbc/mockjdbc-mock` | `partial` | Factories and handlers implemented with tests in `mockjdbc-mock/src/test/java` | Deferred: Phase 1 statement-family contract, then Phase 2 result/value fidelity (`docs/next-steps.md`) |
| `mockjdbc/mockjdbc-proto` | `active` | Protobuf schemas, generated stubs, and Buf `FILE` compatibility gate | Deferred: evolve contracts only for explicitly supported fidelity work (`docs/next-steps.md`, Phase 2) |
| `mockjdbc/mockjdbc-proxy` | `partial`, `active` | Production classes and tests under `mockjdbc-proxy/src/main/java` and `mockjdbc-proxy/src/test/java`; Docker-backed Kafka integration passes in CI (ADR-0005) | Deferred/optional: masking is not implemented; evaluate for debugging data-handling needs in `docs/next-steps.md`, Phase 3 |
| `mockjdbc/mockjdbc-wiremock` | `partial`, `active` | Bridge classes + tests in `mockjdbc-wiremock/src/main/java` and `mockjdbc-wiremock/src/test/java`; Docker-backed Kafka integration passes in CI (ADR-0005) | Deferred/optional: retries/dead-letter handling are not implemented and are not production-readiness gates; see `docs/next-steps.md`, Phase 3 |
| `mockjdbc-spring-users` | `partial` | Independent app with tests for `h2` and `mockjdbc` profiles; mock-profile E2E requires Docker and is exercised in CI | Maintain the documented reactor install and built-jar prerequisites when the sample changes |

## Decision notes

- `mockjdbc-proxy` is no longer a pure placeholder; it is now partial/active and tracked by ADR-0002.
- `mockjdbc-wiremock` is introduced as a new partial/active bridge module tracked by ADR-0004.

## Maintenance rule

Update this file in the same change set whenever a module changes status, scope, or target milestone.
