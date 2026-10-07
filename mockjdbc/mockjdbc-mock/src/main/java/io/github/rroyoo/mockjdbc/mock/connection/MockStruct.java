package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Arrays;
import java.util.Map;

final class MockStruct implements Struct {

    private final String sqlTypeName;
    private final Object[] attributes;

    MockStruct(String sqlTypeName, Object[] attributes) {
        this.sqlTypeName = sqlTypeName;
        this.attributes = attributes.clone();
    }

    @Override
    public String getSQLTypeName() {
        return sqlTypeName;
    }

    @Override
    public Object[] getAttributes() {
        return attributes.clone();
    }

    @Override
    public Object[] getAttributes(Map<String, Class<?>> map) throws SQLException {
        return getAttributes();
    }

    @Override
    public String toString() {
        return sqlTypeName + Arrays.toString(attributes);
    }
}
