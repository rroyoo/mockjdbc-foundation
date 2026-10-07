package io.github.rroyoo.mockjdbc.wiremock;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class KafkaMappingConsumerTest {

    @Test
    @DisplayName("Given MockConsumer, consumer loop parses record and delegates to registrar")
    void shouldProcessKafkaRecord() {
        var topic = "mockjdbc.query.events";
        var partition = new TopicPartition(topic, 0);

        var mockConsumer = new MockConsumer<String, byte[]>(OffsetResetStrategy.EARLIEST);

        var registrar = mock(WireMockMappingRegistrar.class);
        var kafkaConsumer = new KafkaMappingConsumer(mockConsumer, registrar, topic, Duration.ofMillis(50));

        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        // Simulate partition assignment: consumer must seek to beginning (offset 0),
        // ensuring historic events are replayed on every WireMock startup.
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(java.util.List.of(partition));
            mockConsumer.updateBeginningOffsets(Map.of(partition, 0L));
            // Advance position to simulate a prior committed offset of 5.
            // The seek-to-beginning listener should reset it back to 0.
            mockConsumer.seek(partition, 5L);
            mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 5L, "users-primary", event.toByteArray()));
        });

        kafkaConsumer.start();

        verify(registrar, timeout(1000)).upsert(org.mockito.ArgumentMatchers.any(MockedQuery.class));

        kafkaConsumer.close();
    }

    private static final String TOPIC = "mockjdbc.query.events";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private static MockedQuery event(String sql) {
        return MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql(sql).build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();
    }

    private static MockConsumer<String, byte[]> consumerWith(byte[]... payloads) {
        var mockConsumer = new MockConsumer<String, byte[]>(OffsetResetStrategy.EARLIEST);
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(java.util.List.of(PARTITION));
            mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
            long offset = 0;
            for (byte[] payload : payloads) {
                mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, offset++, "k", payload));
            }
        });
        return mockConsumer;
    }

    private static Long committedOffset(MockConsumer<String, byte[]> consumer) {
        var committed = consumer.committed(java.util.Set.of(PARTITION)).get(PARTITION);
        return committed == null ? null : committed.offset();
    }

    @Test
    @DisplayName("Given two events for the same stub, registrations happen in Kafka offset order")
    void shouldRegisterSameKeyEventsInOrder() {
        var first = event("SELECT 1");
        var second = event("select  1;");
        var mockConsumer = consumerWith(first.toByteArray(), second.toByteArray());
        var registrar = mock(WireMockMappingRegistrar.class);
        var kafkaConsumer = new KafkaMappingConsumer(mockConsumer, registrar, TOPIC, Duration.ofMillis(20));

        kafkaConsumer.start();
        try {
            var inOrder = org.mockito.Mockito.inOrder(registrar);
            inOrder.verify(registrar, timeout(2000)).upsert(first);
            inOrder.verify(registrar, timeout(2000)).upsert(second);
        } finally {
            kafkaConsumer.close();
        }
    }

    @Test
    @DisplayName("Given successful registrations, offset is committed only after the batch is registered")
    void shouldCommitOffsetAfterRegistration() throws Exception {
        var mockConsumer = consumerWith(event("SELECT 1").toByteArray(), event("SELECT 2").toByteArray());
        var registrar = mock(WireMockMappingRegistrar.class);
        var committedAtRegistration = new java.util.concurrent.atomic.AtomicReference<Long>(-1L);
        org.mockito.Mockito.doAnswer(invocation -> {
            committedAtRegistration.compareAndSet(-1L, committedOffset(mockConsumer));
            return true;
        }).when(registrar).upsert(org.mockito.ArgumentMatchers.any(MockedQuery.class));
        var kafkaConsumer = new KafkaMappingConsumer(mockConsumer, registrar, TOPIC, Duration.ofMillis(20));

        kafkaConsumer.start();
        try {
            long deadline = System.currentTimeMillis() + 2000;
            while (!Long.valueOf(2L).equals(committedOffset(mockConsumer)) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            org.junit.jupiter.api.Assertions.assertEquals(2L, committedOffset(mockConsumer));
            org.junit.jupiter.api.Assertions.assertNull(committedAtRegistration.get());
        } finally {
            kafkaConsumer.close();
        }
    }

    @Test
    @DisplayName("Given failing registration, offset is not committed and the partition is rewound for retry")
    void shouldNotCommitAndRewindOnRegistrationFailure() throws Exception {
        var mockConsumer = consumerWith(event("SELECT 1").toByteArray());
        var registrar = mock(WireMockMappingRegistrar.class);
        org.mockito.Mockito.when(registrar.upsert(org.mockito.ArgumentMatchers.any(MockedQuery.class)))
                .thenThrow(new IllegalStateException("wiremock down"));
        var kafkaConsumer = new KafkaMappingConsumer(mockConsumer, registrar, TOPIC, Duration.ofMillis(20));

        kafkaConsumer.start();
        try {
            verify(registrar, timeout(2000)).upsert(org.mockito.ArgumentMatchers.any(MockedQuery.class));
            Thread.sleep(150);
            org.junit.jupiter.api.Assertions.assertNull(committedOffset(mockConsumer));
            org.junit.jupiter.api.Assertions.assertEquals(0L, mockConsumer.position(PARTITION));
        } finally {
            kafkaConsumer.close();
        }
    }
}
