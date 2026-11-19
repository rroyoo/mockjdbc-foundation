package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;

/** Creates in-memory {@link Array} and {@link Struct} values for the {@code Connection} factory methods. */
final class StructuredTypeHandler {

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        if (typeName == null || typeName.isBlank()) {
            throw new SQLException("Array type name must not be null or blank");
        }
        if (elements == null) {
            throw new SQLException("Array elements must not be null");
        }
        return new MockArray(typeName, elements);
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        if (typeName == null || typeName.isBlank()) {
            throw new SQLException("Struct type name must not be null or blank");
        }
        if (attributes == null) {
            throw new SQLException("Struct attributes must not be null");
        }
        return new MockStruct(typeName, attributes);
    }
}
