---
name: kafka-consumer-lifecycle
description: Patterns for implementing a thread-safe, cleanly stoppable Kafka consumer lifecycle suitable for test and production use.
---
# kafka-consumer-lifecycle
## When to use
Use this skill when implementing `KafkaMappingConsumer` or any Kafka consumer that needs controlled start/stop behavior.
## Actions
### Consumer thread management
- Use a single dedicated daemon thread for the consumer poll loop.
- Name the thread explicitly: `mockjdbc-wiremock-kafka-consumer`.
- Keep the `AtomicBoolean running` flag for cooperative stop.
### Poll loop pattern
```java
while (running.get()) {
    var records = consumer.poll(Duration.ofMillis(pollTimeoutMillis));
    for (var record : records) {
        processRecord(record);
    }
}
```
### Clean shutdown
- Set `running.set(false)` first.
- Call `consumer.wakeup()` to interrupt a blocking poll.
- Join the thread with timeout (e.g. 2 seconds) before returning from `close()`.
- Catch `WakeupException` in the poll loop and break cleanly.
### Error handling in poll loop
- Catch `Exception` per record: a bad record must not stop the consumer.
- Log the error with the record offset and key for traceability.
- Continue processing the remaining records in the batch.
### Deserialization
- Use `ByteArrayDeserializer` for values and parse `MockedQuery.parseFrom(bytes)` per record.
- If parsing fails, log and skip rather than retrying indefinitely.
### Commit strategy
- Default: auto-commit (`enable.auto.commit=true`) is acceptable for at-least-once delivery.
- For exactly-once stub idempotency, disable auto-commit and commit after successful stub registration.
## Expected output
- A consumer class with `start()` and `close()` methods.
- A poll loop that is cleanly interruptible and handles per-record errors.
