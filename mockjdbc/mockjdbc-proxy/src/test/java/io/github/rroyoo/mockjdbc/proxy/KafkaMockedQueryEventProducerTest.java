package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaMockedQueryEventProducerTest {

    @Test
    @DisplayName("Given a mocked producer, when sending event, then it publishes protobuf bytes with datasource key")
    void shouldPublishMockedQueryAsKafkaRecord() throws Exception {
        @SuppressWarnings("unchecked")
        var kafkaProducer = (Producer<String, byte[]>) mock(Producer.class);

        var producer = new KafkaMockedQueryEventProducer(
                kafkaProducer,
                "mockjdbc.query.events",
                KafkaMockedQueryEventProducer.datasourceKeyResolver()
        );

        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .build();

        producer.send(event);

        @SuppressWarnings("unchecked")
        var argument = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaProducer).send(argument.capture(), any());

        var record = argument.getValue();
        assertEquals("mockjdbc.query.events", record.topic());
        assertEquals("users-primary", record.key());
        var payload = (byte[]) record.value();
        assertTrue(payload.length > 0);
    }
}
