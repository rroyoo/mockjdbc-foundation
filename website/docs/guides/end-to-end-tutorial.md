---
id: end-to-end-tutorial
title: End-to-end tutorial (record, bridge, replay)
sidebar_position: 2
---

This tutorial walks through the full MockJDBC flow using the [`mockjdbc-spring-users`](https://github.com/rroyoo/mockjdbc-foundation/tree/main/mockjdbc-spring-users) sample app, which exercises all three profiles against the same small Users API (`GET/POST/PUT/DELETE /users`).

## 1. Build the reactor

The sample app resolves `mockjdbc-mock`, `mockjdbc-proto`, and `mockjdbc-proxy` as regular Maven coordinates from your local repository, so install them first:

```bash
cd mockjdbc-foundation/mockjdbc
mvn clean install -DskipTests
```

## 2. Start the capture-to-replay infrastructure

The sample ships a `docker-compose.yml` that starts Kafka, downloads the WireMock gRPC extension, copies in the `mockjdbc-wiremock` bridge jar, and compiles the proto descriptors WireMock needs:

```bash
cd ../mockjdbc-spring-users

# the wiremock-kafka-bridge-extension service copies this jar in, so build it first
cd ../mockjdbc && mvn -pl mockjdbc-wiremock -am package -DskipTests && cd ../mockjdbc-spring-users

docker compose up -d wiremock-grpc
```

This brings up Kafka and a WireMock gRPC server with the `mockjdbc-wiremock` bridge extension loaded, consuming `mockjdbc.query.events` and ready to register stubs.

## 3. Capture real traffic with the proxy profile

Run the app with the `proxy` profile, which wraps its H2 `DataSource` with the `mockjdbc-proxy` agent and publishes captured activity to Kafka:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=proxy \
  -Dspring-boot.run.jvmArguments="-javaagent:../mockjdbc/mockjdbc-proxy/target/mockjdbc-proxy-0.1.0-SNAPSHOT.jar"
```

Exercise the API so there is real SQL to capture:

```bash
curl -X POST localhost:8080/users -H 'Content-Type: application/json' -d '{"name":"Ada"}'
curl localhost:8080/users/1
```

Every statement your app runs against its `DataSource` is now flowing through the agent as `MockedQuery` protobuf events on the `mockjdbc.query.events` Kafka topic.

## 4. Let the bridge register stubs

The `wiremock-grpc` service's bridge extension consumes those events in the background and upserts deterministic WireMock stubs — no action needed on your part. Give it a moment to process the events you just generated.

## 5. Replay without a database, using the mock profile

Stop the `proxy`-profile app, then start the same app with the `mockjdbc` profile, which points its `DataSource` at `jdbc:mock://localhost:8088` instead of H2:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mockjdbc
```

```bash
curl localhost:8080/users/1
```

The response comes back from the **mock driver**, resolved via gRPC against the WireMock stub the bridge generated from your earlier `proxy`-profile traffic — with no live H2 database involved at all.

## What the automated test does

`RecordReplayEndToEndTest` in the sample app automates exactly this flow with Testcontainers: it captures an H2 query through the proxy, waits for the bridge-generated WireMock mapping to appear, then replays it through the mock driver without any hand-written stub. Run it with:

```bash
mvn test
```

Docker must be available; the stack is started by Testcontainers automatically (no need to run `docker compose` manually for the test).

:::note Replay is stateless
`commit`/`rollback` are tracked but are no-ops during replay — updates and deletes return their recorded JDBC outcome, not a mutation of later query results. See [Known limitations](../reference/known-limitations.md).
:::
