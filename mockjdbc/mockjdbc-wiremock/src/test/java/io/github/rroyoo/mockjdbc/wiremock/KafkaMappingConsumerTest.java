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

        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(java.util.List.of(partition));
            mockConsumer.updateBeginningOffsets(Map.of(partition, 0L));
            mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 0L, "users-primary", event.toByteArray()));
        });

        kafkaConsumer.start();

        verify(registrar, timeout(1000)).upsert(org.mockito.ArgumentMatchers.any(MockedQuery.class));

        kafkaConsumer.close();
    }
}
