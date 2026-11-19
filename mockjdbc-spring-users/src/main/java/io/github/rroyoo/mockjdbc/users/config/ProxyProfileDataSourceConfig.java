package io.github.rroyoo.mockjdbc.users.config;

import io.github.rroyoo.mockjdbc.proxy.AsyncDispatchConfig;
import io.github.rroyoo.mockjdbc.proxy.JdbcAgentRegistry;
import io.github.rroyoo.mockjdbc.proxy.JdbcCaptureRegistration;
import io.github.rroyoo.mockjdbc.proxy.KafkaMockedQueryEventProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@Profile("proxy")
public class ProxyProfileDataSourceConfig {

    @Bean(destroyMethod = "close")
    ProxyDataSourceLifecycle proxyDataSourceLifecycle(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${mockjdbc.proxy.datasource-id:users-proxy}") String datasourceId,
            @Value("${mockjdbc.proxy.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${mockjdbc.proxy.kafka.topic:mockjdbc.query.events}") String topic,
            @Value("${mockjdbc.proxy.kafka.acks:all}") String acks,
            @Value("${mockjdbc.proxy.kafka.retries:3}") int retries
    ) {
        var targetDataSource = new DriverManagerDataSource();
        targetDataSource.setDriverClassName(driverClassName);
        targetDataSource.setUrl(url);
        targetDataSource.setUsername(username);
        targetDataSource.setPassword(password);

        var kafkaProperties = new Properties();
        kafkaProperties.setProperty("bootstrap.servers", bootstrapServers);
        kafkaProperties.setProperty("acks", acks);
        kafkaProperties.setProperty("retries", String.valueOf(retries));

        var producer = KafkaMockedQueryEventProducer.create(
                kafkaProperties,
                topic,
                KafkaMockedQueryEventProducer.datasourceKeyResolver()
        );

        var registration = JdbcAgentRegistry.register(
                targetDataSource,
                datasourceId,
                event -> {
                },
                mockedQuery -> {
                    try {
                        producer.send(mockedQuery);
                    } catch (Exception exception) {
                        throw new RuntimeException("Failed to publish MockedQuery event to Kafka", exception);
                    }
                },
                AsyncDispatchConfig.defaults()
        );

        return new ProxyDataSourceLifecycle(targetDataSource, registration, producer);
    }

    @Bean
    @Primary
    DataSource dataSource(ProxyDataSourceLifecycle lifecycle) {
        return lifecycle.dataSource();
    }

    static final class ProxyDataSourceLifecycle implements AutoCloseable {
        private final DataSource dataSource;
        private final JdbcCaptureRegistration registration;
        private final KafkaMockedQueryEventProducer producer;

        ProxyDataSourceLifecycle(DataSource dataSource,
                                 JdbcCaptureRegistration registration,
                                 KafkaMockedQueryEventProducer producer) {
            this.dataSource = dataSource;
            this.registration = registration;
            this.producer = producer;
        }

        DataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() {
            try {
                JdbcAgentRegistry.unregister(dataSource);
                registration.close();
            } finally {
                producer.close();
            }
        }
    }
}
