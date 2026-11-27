# Contributing to MockJDBC

Thanks for your interest in contributing. MockJDBC is a debugging/testing tool, not a production dependency — keep that in mind when evaluating whether a change is in scope.

## Before you start

- Check [`docs/module-status-matrix.md`](docs/module-status-matrix.md) and [`docs/jdbc-supported-surface.md`](docs/jdbc-supported-surface.md) to understand current module maturity and supported JDBC behavior.
- Check [`docs/next-steps.md`](docs/next-steps.md) (JDBC implementation backlog) and [`docs/open-source-launch-roadmap.md`](docs/open-source-launch-roadmap.md) (community/launch backlog) for planned work before starting something large; open an issue first to avoid duplicated effort.
- For bugs or feature requests, open a GitHub issue using the provided templates.
- For questions, use [GitHub Discussions](https://github.com/rroyoo/mockjdbc-foundation/discussions) or open an issue.

## Project layout

This repository contains two independent Maven builds:

- `mockjdbc/` — the multi-module reactor for the reusable libraries (`mockjdbc-proto`, `mockjdbc-proxy`, `mockjdbc-mock`, `mockjdbc-wiremock`).
- `mockjdbc-spring-users/` — an independent Spring Boot sample app, not part of the reactor.

Module boundaries are intentional:

- `mockjdbc-proto` owns the Protobuf/gRPC contracts.
- `mockjdbc-proxy` owns JDBC capture and event publication.
- `mockjdbc-wiremock` owns Kafka-to-stub registration.
- `mockjdbc-mock` owns replay-side JDBC behavior.

Keep changes within the module that owns the concern; cross-module behavior changes (for example, a Protobuf field used by capture and replay) must update every affected module and its tests together.

## Build and test

The reactor targets Java 17 and requires Maven 3.9.6+. CI runs on Java 21 and requires Docker for Kafka/WireMock integration tests (Testcontainers).

```bash
# Build and test the reusable modules
cd mockjdbc
mvn clean verify

# Run one module and its reactor dependencies
mvn -pl mockjdbc-mock -am test

# Run one test class or method
mvn -pl mockjdbc-mock -am -Dtest=ConnectionFactoryTest test
mvn -pl mockjdbc-mock -am -Dtest=ConnectionFactoryTest#shouldCreateConnectionFromMockConfig test
```

To build/run the sample app against the reactor you just built:

```bash
cd mockjdbc && mvn -B -ntp clean install
cd ../mockjdbc-spring-users && mvn -B -ntp verify
```

Every build runs Spotless (Google Java Format, AOSP style) and PMD (cyclomatic complexity threshold 10) during the `validate` phase; both fail the build on violations. Apply formatting with:

```bash
mvn spotless:apply
```

## Coding and testing conventions

- One focused handler per JDBC feature group in `mockjdbc-mock`; handler state is per connection/statement instance, never static.
- Unsupported JDBC operations throw `SQLFeatureNotSupportedException` (or another explicit, documented `SQLException`); never return silent defaults for unsupported behavior.
- Tests use JUnit 5 with behavior-focused `@DisplayName` (Given-When-Then style) and `@ParameterizedTest` for scenario matrices.
- Update `docs/jdbc-supported-surface.md`, `docs/module-status-matrix.md`, or the relevant ADR in the same change whenever behavior or module scope changes.

## Submitting a pull request

- Use the pull request template (`.github/pull_request_template.md`); it is applied automatically.
- Keep changes scoped to the module(s) they affect; avoid unrelated refactors in the same PR.
- Include the commands you ran to validate the change and their outcomes, noting any skipped Docker-backed tests.
- Expect CI to run the full reactor and sample-app builds and a Protobuf compatibility check against `main`. CodeQL analysis runs for same-repository pull requests; fork pull requests still run the build/test checks, but skip CodeQL upload.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License, Version 2.0](LICENSE), the same license that covers this project.
