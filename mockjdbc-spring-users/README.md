# mockjdbc-spring-users

Independent Maven Spring Boot project at the repository root with a basic users API.

## Profiles

- `h2`: uses in-memory H2 datasource.
- `mockjdbc`: uses `io.github.rroyoo.mockjdbc.mock.driver.MockDriver` and an external WireMock gRPC server.
- `proxy`: registers a local datasource with the `mockjdbc-proxy` Java agent and publishes captured `MockedQuery` events to Kafka.

## Endpoints

- `GET /users`
- `GET /users/{id}`
- `POST /users`
- `PUT /users/{id}`
- `DELETE /users/{id}`

## Build and test

The `mockjdbc` profile tests and the record/replay E2E test use Testcontainers to start
the WireMock gRPC/Kafka stack from `docker-compose.yml`. The E2E test captures an H2
query, waits for the bridge-generated WireMock mapping, then replays it through the mock
driver without a hand-written mapping. Docker must be available; there is no need to
start the stack manually before running the tests. Testcontainers uses the local Docker
Compose v2 plugin to support the stack's relative mounts. The sibling MockJDBC reactor
artifacts must be installed first as described below.

The mock JDBC profile replays query responses but does not maintain mutable database
state. `commit` and `rollback` are no-ops; update and delete endpoints demonstrate their
JDBC operation results, not changes visible to later queries.

```bash
mvn test
```

Every build also checks Java formatting with Spotless (Google Java Format, AOSP style)
and cyclomatic complexity with PMD (method/constructor threshold 10). Java 21 or newer is
required to run the formatter. Both checks run during `validate` and fail the build on
violations. Apply the formatter with:

```bash
mvn spotless:apply
```

## Run with h2 profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

## Run with mockjdbc profile

Start WireMock gRPC first:

```bash
docker compose up -d wiremock-grpc
```

Then run the service:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mockjdbc
```

## Run with proxy profile

The `proxy` profile uses the Java agent and emits captured SQL and ResultSet data to Kafka.
Start the JVM with the built agent JAR:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=proxy \
  -Dspring-boot.run.jvmArguments="-javaagent:../mockjdbc/mockjdbc-proxy/target/mockjdbc-proxy-0.1.0-SNAPSHOT.jar"
```

Optional environment overrides:

```bash
export MOCKJDBC_PROXY_KAFKA_BOOTSTRAP=localhost:9092
export MOCKJDBC_PROXY_KAFKA_TOPIC=mockjdbc.query.events
mvn spring-boot:run \
  -Dspring-boot.run.profiles=proxy \
  -Dspring-boot.run.jvmArguments="-javaagent:../mockjdbc/mockjdbc-proxy/target/mockjdbc-proxy-0.1.0-SNAPSHOT.jar"
```

## Local bootstrap (install the reactor first)

This project consumes the reusable modules through regular Maven coordinates, so install them into your local repository (`~/.m2`) before running `mockjdbc-spring-users`.

From the repository root (`mockjdbc-foundation`):

```bash
cd mockjdbc
mvn clean install -DskipTests
cd ../mockjdbc-spring-users
mvn test
```

To run the application manually with the mock profile, start its backend first:

```bash
# From the repository root:
cd mockjdbc-spring-users
docker compose up -d wiremock-grpc
mvn spring-boot:run -Dspring-boot.run.profiles=mockjdbc
```

If the proxy profile is needed, ensure the sibling proxy jar exists first:

```bash
# From the repository root:
cd mockjdbc
mvn -pl mockjdbc-proxy -am package -DskipTests
```

## WireMock assets

- `docker-compose.yml`
- `docker/wiremock/mappings/` contains gRPC stubs for `MockQueryService/FindMock`; SQL matchers use the same normalized form as proxy-generated mappings (trimmed, lowercase, collapsed whitespace, no trailing semicolon)
- descriptors are generated at startup from `../mockjdbc/mockjdbc-proto/src/main/protobuf`
- WireMock extension volume now also includes `mockjdbc-wiremock` bridge jar copied from `../mockjdbc/mockjdbc-wiremock/target`

## Notes

The application resolves `mockjdbc-mock`, `mockjdbc-proto`, and `mockjdbc-proxy` through regular Maven coordinates after the reactor is installed. Two runtime paths also use the reactor's built JARs directly:

- the `proxy` profile attaches `mockjdbc-proxy/target/mockjdbc-proxy-0.1.0-SNAPSHOT.jar` as a Java agent;
- `docker-compose.yml` mounts `mockjdbc-wiremock/target/mockjdbc-wiremock-0.1.0-SNAPSHOT.jar` into WireMock's extensions directory.

Build the reactor before running those profiles so these JARs exist:

```bash
cd mockjdbc
mvn clean install -DskipTests
```
