# Security Policy

MockJDBC is a debugging and testing tool and is **not intended for production deployment**. That said, we take reports of vulnerabilities in the codebase, its dependencies, and its CI/release tooling seriously.

## Supported versions

MockJDBC has not yet published a stable or beta release; `main` is the only actively maintained line. Once beta artifacts are published to Maven Central (see [`docs/open-source-launch-roadmap.md`](docs/open-source-launch-roadmap.md)), this table will be updated to reflect which published versions receive fixes.

| Version | Supported |
|---|---|
| `main` (unreleased) | ✅ |

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Instead, report it privately using one of:

- [GitHub Security Advisories / private vulnerability reporting](https://github.com/rroyoo/mockjdbc-foundation/security/advisories/new) for this repository.
- A direct message to the maintainer, [@rroyoo](https://github.com/rroyoo), through their GitHub profile.

Please include:

- A description of the vulnerability and its potential impact.
- Steps to reproduce it, including affected module(s) (`mockjdbc-proto`, `mockjdbc-proxy`, `mockjdbc-mock`, `mockjdbc-wiremock`, or the sample app).
- Any relevant logs, stack traces, or proof-of-concept code.

We will acknowledge reports as promptly as possible given this is a community-maintained project, investigate, and coordinate disclosure and a fix timeline with the reporter before any public details are shared.

## Scope notes

- Because MockJDBC captures JDBC query text, parameters, and results for replay, do not point the proxy agent at data sources containing data you cannot share with your own Kafka and WireMock infrastructure. Sensitive-data masking is tracked as an open item in the project roadmap, not yet implemented.
- Vulnerabilities in third-party dependencies should ideally be reported upstream as well; Dependabot (`.github/dependabot.yml`) tracks dependency updates for this repository.
