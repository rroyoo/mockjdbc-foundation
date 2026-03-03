package io.github.rroyoo.mockjdbc.mock.connection;

import java.net.PortUnreachableException;
import java.rmi.UnknownHostException;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

public final class MockConnectionPool implements ConnectionPool {

    private final Map<String, Connection> pool;

    public  MockConnectionPool() {
        pool = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public Connection getConnection(String url, Properties properties) {
        if(properties == null || properties.isEmpty()) {
            throw new RuntimeException("The mocked connection pool need the connection properties to create it, but it is null or empty.");
        }

        return pool.computeIfAbsent(url, k -> {
            try {
                return new MockConnection(properties, new GrpcQueryServiceAdapter(properties));
            } catch (PortUnreachableException | UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
