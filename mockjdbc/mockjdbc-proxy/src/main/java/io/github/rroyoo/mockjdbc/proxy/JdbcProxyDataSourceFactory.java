package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Consumer;

public final class JdbcProxyDataSourceFactory {

    private JdbcProxyDataSourceFactory() {}

    public static ProxyBinding wrap(DataSource targetDataSource,
                                    String datasourceId,
                                    Consumer<JdbcQueryInterceptedEvent> localEventConsumer,
                                    Consumer<MockedQuery> protoEventConsumer,
                                    AsyncDispatchConfig config) {
        var listener = new JdbcQueryCaptureListener(
                datasourceId,
                Objects.requireNonNull(localEventConsumer, "localEventConsumer is required"),
                MockedQueryEventProducer.fromConsumer(Objects.requireNonNull(protoEventConsumer, "protoEventConsumer is required")),
                Objects.requireNonNull(config, "config is required")
        );

        var proxiedDataSource = ProxyDataSourceBuilder
                .create(Objects.requireNonNull(targetDataSource, "targetDataSource is required"))
                .name(datasourceId)
                .listener(listener)
                .buildProxy();

        return new ProxyBinding(proxiedDataSource, listener);
    }

    public static ProxyBinding wrap(DataSource targetDataSource,
                                    String explicitAlias,
                                    String beanName,
                                    Consumer<JdbcQueryInterceptedEvent> localEventConsumer,
                                    Consumer<MockedQuery> protoEventConsumer,
                                    AsyncDispatchConfig config) {
        var resolvedDatasourceId = DatasourceIdentityResolver.resolve(explicitAlias, beanName, targetDataSource);
        return wrap(targetDataSource, resolvedDatasourceId, localEventConsumer, protoEventConsumer, config);
    }

    public record ProxyBinding(DataSource dataSource, JdbcQueryCaptureListener listener) implements AutoCloseable {
        @Override
        public void close() {
            listener.close();
        }
    }
}
