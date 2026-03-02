package io.github.rroyoo.mockjdbc.proxy;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import javax.sql.DataSource;

public final class DefaultDataSourceProxyFactory implements DataSourceProxyFactory {

    @Override
    public DataSource create(DataSource originalDataSource) {
        return ProxyDataSourceBuilder
                .create()
                .buildProxy();
    }
}
