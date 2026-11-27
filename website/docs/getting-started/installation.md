---
id: installation
title: Installation
sidebar_position: 1
---

## Requirements

- Java 17+ (CI builds and tests on Java 21)
- Maven 3.9.6 or newer (required by the Protobuf build plugin)
- Docker, only if you want to run the Kafka/WireMock integration tests

## Maven Central (beta artifacts)

Beta artifacts are published to Maven Central under the `io.github.rroyoo.mockjdbc` group. Add the modules you need:

```xml
<dependency>
    <groupId>io.github.rroyoo.mockjdbc</groupId>
    <artifactId>mockjdbc-mock</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

Available artifacts:

| Artifact | Use it for |
|---|---|
| `mockjdbc-proto` | Protobuf/gRPC contracts (transitive dependency of the other modules). |
| `mockjdbc-proxy` | The capture agent, attached as a Java agent to the application you want to record. |
| `mockjdbc-wiremock` | The Kafka-to-WireMock bridge extension. |
| `mockjdbc-mock` | The replay-side JDBC driver (`jdbc:mock://...`). |

:::note
Publication status and the exact first published version are tracked in the [open-source launch roadmap](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/open-source-launch-roadmap.md). Until a beta is live, build from source as described below.
:::

## Build from source

```bash
git clone https://github.com/rroyoo/mockjdbc-foundation.git
cd mockjdbc-foundation/mockjdbc
mvn -B -ntp clean install
```

This installs `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, and `mockjdbc-wiremock` into your local Maven repository (`~/.m2`) so they can be consumed as regular Maven coordinates by other local projects, including the sample app.

## Run the sample application

The repository ships a runnable Spring Boot sample, [`mockjdbc-spring-users`](https://github.com/rroyoo/mockjdbc-foundation/tree/main/mockjdbc-spring-users), demonstrating all three profiles (H2, proxy capture, mock replay):

```bash
cd ../mockjdbc-spring-users
mvn -B -ntp verify
```

Continue with [capturing SQL with the proxy agent](capturing-with-the-proxy-agent.md) or jump straight to the [end-to-end tutorial](../guides/end-to-end-tutorial.md).
