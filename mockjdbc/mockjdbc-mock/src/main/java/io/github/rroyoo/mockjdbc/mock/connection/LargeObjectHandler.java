package io.github.rroyoo.mockjdbc.mock.connection;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;

/** Creates in-memory LOB instances for {@code Connection.createBlob/createClob/createNClob}. */
final class LargeObjectHandler {

    public Blob createBlob() throws SQLException {
        return new SerialBlob(new byte[0]);
    }

    public Clob createClob() throws SQLException {
        return new SerialClob(new char[0]);
    }

    public NClob createNClob() throws SQLException {
        var delegate = new SerialClob(new char[0]);
        return (NClob) Proxy.newProxyInstance(
                LargeObjectHandler.class.getClassLoader(),
                new Class<?>[]{ NClob.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "MockNClob";
                        };
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }

    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("Connection.createSQLXML is not supported by MockJDBC");
    }
}
