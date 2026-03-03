package io.github.rroyoo.mockjdbc.proxy;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import javax.sql.DataSource;
import java.util.Objects;

public final class DefaultDataSourceProxyFactory implements DataSourceProxyFactory {

    @Override
    public DataSource create(DataSource originalDataSource) {
        var targetDataSource = Objects.requireNonNull(originalDataSource, "originalDataSource must not be null");

        return ProxyDataSourceBuilder
                .create(targetDataSource)
                .build();
    }
}
