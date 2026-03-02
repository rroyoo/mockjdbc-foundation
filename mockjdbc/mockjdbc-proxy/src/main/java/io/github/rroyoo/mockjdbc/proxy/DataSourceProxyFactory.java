package io.github.rroyoo.mockjdbc.proxy;

import javax.sql.DataSource;

public interface DataSourceProxyFactory {
    DataSource create(DataSource originalDataSource);
}
