# MockJDBC

[![CI](https://github.com/rroyoo/mockjdbc-foundation/actions/workflows/ci-build-and-test.yml/badge.svg?branch=main)](https://github.com/rroyoo/mockjdbc-foundation/actions/workflows/ci-build-and-test.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**MockJDBC is a record/replay toolkit for debugging and testing JDBC-based applications.** It captures real SQL activity from a running application and lets you replay it later as a drop-in JDBC driver, without a live database.

> **Status: pre-release / beta preparation.** MockJDBC is a debugging and testing tool. It is **not intended for production deployment**, and its JDBC coverage is intentionally partial. See [Known limitations](#known-limitations) before adopting it.

## Why MockJDBC

Testing or debugging code that talks to a database usually means either running a real database in every environment, or hand-writing mocks for every query shape you care about. MockJDBC offers a third option: **capture what your application actually does against JDBC, then replay it deterministically** — in CI, in a debugging session, or in an isolated environment with no database at all.

## How it works

```text
real DataSource
  -> mockjdbc-proxy agent        (captures SQL, parameters, results)
  -> MockedQuery protobuf event
  -> Kafka
  -> mockjdbc-wiremock bridge    (maps events to deterministic stubs)
  -> WireMock gRPC stub
  -> mockjdbc-mock driver        (jdbc:mock://... replays the captured result)
  -> application JDBC result
```

1. **Record:** `mockjdbc-proxy` instruments a `DataSource` and publishes captured query/parameter/result activity as `MockedQuery` events.
2. **Bridge:** `mockjdbc-wiremock` consumes those events from Kafka and registers them as deterministic WireMock stubs.
3. **Replay:** `mockjdbc-mock` is a real `java.sql.Driver` (`jdbc:mock://...`) that resolves queries against those stubs over gRPC, so your application code runs unmodified against a replayed backend.

## Modules

| Module | Role |
|---|---|
| [`mockjdbc-proto`](mockjdbc/mockjdbc-proto) | Protobuf/gRPC contracts shared by every module. |
| [`mockjdbc-proxy`](mockjdbc/mockjdbc-proxy) | Java agent that captures JDBC activity and publishes `MockedQuery` events. |
| [`mockjdbc-wiremock`](mockjdbc/mockjdbc-wiremock) | WireMock extension that turns captured events into stub mappings. |
| [`mockjdbc-mock`](mockjdbc/mockjdbc-mock) | The replay-side JDBC driver (`jdbc:mock://...`). |
| [`mockjdbc-spring-users`](mockjdbc-spring-users) | A runnable Spring Boot sample demonstrating all three profiles (H2, proxy capture, mock replay). |

Current module maturity is tracked in [`docs/module-status-matrix.md`](docs/module-status-matrix.md); supported JDBC behavior is tracked in [`docs/jdbc-supported-surface.md`](docs/jdbc-supported-surface.md).

## Getting started

Maven Central publication of beta artifacts is in progress (tracked in [`docs/open-source-launch-roadmap.md`](docs/open-source-launch-roadmap.md)). Until the first beta is published, build and consume MockJDBC from source:

```bash
# Requires Maven >= 3.9.6 and Java 17+
git clone https://github.com/rroyoo/mockjdbc-foundation.git
cd mockjdbc-foundation/mockjdbc
mvn -B -ntp clean install
```

This installs `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, and `mockjdbc-wiremock` into your local Maven repository under coordinates such as:

```xml
<dependency>
    <groupId>io.github.rroyoo.mockjdbc</groupId>
    <artifactId>mockjdbc-mock</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

To see the full record/replay flow working end to end, run the sample application:

```bash
cd ../mockjdbc-spring-users
mvn -B -ntp verify
```

See [`mockjdbc-spring-users/README.md`](mockjdbc-spring-users/README.md) for profile-by-profile instructions (H2, proxy capture, mock replay).

A dedicated user documentation site (Docusaurus) is planned; see the [open-source launch roadmap](docs/open-source-launch-roadmap.md) for status.

## Known limitations

- **Not for production.** MockJDBC is intended for debugging, testing, and local/CI development workflows.
- **Partial JDBC coverage.** `Connection` is largely closed; `Statement`/`PreparedStatement`/`CallableStatement` support is broad but not complete (see [`docs/jdbc-supported-surface.md`](docs/jdbc-supported-surface.md)). Unsupported operations fail explicitly with `SQLFeatureNotSupportedException` rather than silently returning defaults.
- **Replay is stateless.** `commit`/`rollback` are tracked but are no-ops; replay does not simulate transactional database state changes.
- **No sensitive-data masking yet.** Captured SQL and parameter values are published as-is; do not point the proxy at datasources containing data you cannot share with your Kafka/WireMock infrastructure.
- **Requires Docker for some tests.** Kafka/WireMock integration tests use Testcontainers.

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) for build/test commands, module boundaries, and pull request expectations, and [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) before participating. Report security issues as described in [`SECURITY.md`](SECURITY.md) rather than filing a public issue.

For engineering/architecture documentation (ADRs, module status, roadmap), see [`docs/`](docs) and [`mockjdbc/README.md`](mockjdbc/README.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
