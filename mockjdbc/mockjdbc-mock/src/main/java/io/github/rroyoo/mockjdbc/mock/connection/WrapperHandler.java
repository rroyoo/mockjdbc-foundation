package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;

final class WrapperHandler {

    private final Object self;

    WrapperHandler(Object self) {
        this.self = self;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        requireInterface(iface);
        return iface.isInstance(self);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        requireInterface(iface);
        if (iface.isInstance(self)) {
            return iface.cast(self);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    private static void requireInterface(Class<?> iface) throws SQLException {
        if (iface == null) {
            throw new SQLException("Interface must not be null");
        }
    }
}
