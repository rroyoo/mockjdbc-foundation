---
id: contributing
title: Contributing and release status
sidebar_position: 5
---

## Contributing

MockJDBC welcomes contributions. Start with:

- [`CONTRIBUTING.md`](https://github.com/rroyoo/mockjdbc-foundation/blob/main/CONTRIBUTING.md) — build/test commands, module boundaries, and pull request expectations.
- [`CODE_OF_CONDUCT.md`](https://github.com/rroyoo/mockjdbc-foundation/blob/main/CODE_OF_CONDUCT.md) — community standards (Contributor Covenant 2.1).
- [`SECURITY.md`](https://github.com/rroyoo/mockjdbc-foundation/blob/main/SECURITY.md) — report vulnerabilities privately rather than filing a public issue.

Pick up an issue labeled [`good first issue`](https://github.com/rroyoo/mockjdbc-foundation/labels/good%20first%20issue) or [`help wanted`](https://github.com/rroyoo/mockjdbc-foundation/labels/help%20wanted), or start a [discussion](https://github.com/rroyoo/mockjdbc-foundation/discussions) if you're unsure where to start.

## Module boundaries

| Module | Owns |
|---|---|
| `mockjdbc-proto` | Protobuf/gRPC contracts consumed by every other module |
| `mockjdbc-proxy` | JDBC capture and Kafka event publication |
| `mockjdbc-wiremock` | Kafka-to-WireMock-stub bridge |
| `mockjdbc-mock` | Replay-side JDBC driver |

Keep transport/integration concerns in the module that owns them — for example, don't move Kafka or gRPC transport logic into JDBC statement handlers.

## Project status documents

MockJDBC tracks its own status transparently rather than aspirationally:

- [Module status matrix](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/module-status-matrix.md) — per-module maturity (`active`/`partial`/`placeholder`/`experimental`/`deferred`) and next milestones.
- [Supported JDBC surface](reference/supported-jdbc-surface.md) — exactly which JDBC methods are implemented, partial, or unsupported.
- [Open-source launch roadmap](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/open-source-launch-roadmap.md) — the public launch plan: repository contract, community files, Maven Central beta publishing, and this documentation site.
- [Architecture Decision Records](https://github.com/rroyoo/mockjdbc-foundation/tree/main/docs/adr) — the rationale behind significant design decisions (e.g. the proxy's lock-free ring buffer, the mock driver's JDBC scope).

## Release status

MockJDBC is in **pre-release / beta preparation**. There is no stable `1.0.0` release, and no beta artifacts have been published to Maven Central yet. The planned beta format is `0.x.0-beta.N`; publication requires maintainer credentials and a tagged release, followed by manual publication in the Central Portal. Track the release prerequisites and status in the [open-source launch roadmap](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/open-source-launch-roadmap.md).
