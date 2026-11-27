---
id: troubleshooting
title: Troubleshooting
sidebar_position: 4
---

## `mvn clean install` fails with a Maven version error

The reactor requires **Maven 3.9.6+** because `flatten-maven-plugin` enforces that minimum. `mvn validate` (which doesn't trigger flattening) can still succeed on older Maven, but any full lifecycle (`clean`, `install`, `package`, `verify`) will fail with a version-requirement error on older Maven. Upgrade your local Maven, or use a version manager (e.g. [sdkman](https://sdkman.io/)) to install 3.9.6+ alongside your system Maven.

## `DriverManager` doesn't find `jdbc:mock://...`

`MockDriver` self-registers via `META-INF/services/java.sql.Driver`, so it should be discovered automatically once `mockjdbc-mock` is on the classpath — no manual `Class.forName(...)` is required. If it still isn't found, confirm the jar is actually on the runtime classpath (not just a test-scope or provided-scope dependency) and that the URL matches the expected pattern (`jdbc:mock://<host>:<port>` with digits-only port).

## Mock driver queries return empty/default results

This almost always means the WireMock gRPC backend has no stub registered for that **normalized** SQL. Check:
- The bridge (`mockjdbc-wiremock`) is actually running and consuming the Kafka topic the proxy agent published to.
- Normalization is consistent: trim, lowercase, collapse whitespace, strip trailing semicolon. A query that differs only in whitespace or case from what was captured still normalizes to the same key, but a semantically different query will not match.
- The datasource id used when registering the proxy matches the one embedded in the stub key (`<datasource_id>::<normalized_sql>`).

## No events reaching Kafka from the proxy agent

- Confirm the agent is actually attached (`-javaagent:/path/to/mockjdbc-proxy.jar`) and the JVM printed no instrumentation errors at startup.
- Confirm `JdbcAgentRegistry.register(...)` was called for the `DataSource` your application actually uses (registration is per-`DataSource` instance, not global).
- Check `AsyncMockedQueryEventDispatcher.stats()` — a growing `dropped` counter under load usually means the ring buffer is undersized for your throughput; increase `ringBufferCapacity` in `AsyncDispatchConfig`.
- Verify Kafka connectivity/credentials independently of MockJDBC (e.g. with `kafka-console-consumer`) before assuming the agent is at fault.

## Kafka/WireMock integration tests are skipped

These tests use Testcontainers and require Docker. If Docker (or Docker Compose v2) isn't available in your environment, the tests are skipped rather than failing the build. Install Docker to exercise them locally, or rely on CI, which has Docker available.

## GPG signing fails during `-Prelease verify`

This is expected when no GPG key is configured locally — the `release` Maven profile (source/javadoc/GPG/Central-publishing plugins) is only meant to run with real signing credentials, normally inside the [release workflow](https://github.com/rroyoo/mockjdbc-foundation/blob/main/.github/workflows/release.yml). Day-to-day development builds should not use `-Prelease`.

## Still stuck?

Open a [bug report](https://github.com/rroyoo/mockjdbc-foundation/issues/new/choose) with repro steps, or start a [discussion](https://github.com/rroyoo/mockjdbc-foundation/discussions) for usage questions.
