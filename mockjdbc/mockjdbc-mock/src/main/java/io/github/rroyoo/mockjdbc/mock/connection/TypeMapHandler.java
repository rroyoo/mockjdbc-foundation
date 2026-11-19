package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

final class TypeMapHandler {

    private volatile Map<String, Class<?>> typeMap = Map.of();

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return new HashMap<>(typeMap);
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        if (map == null) {
            throw new SQLException("Type map must not be null");
        }
        typeMap = Map.copyOf(map);
    }
}
