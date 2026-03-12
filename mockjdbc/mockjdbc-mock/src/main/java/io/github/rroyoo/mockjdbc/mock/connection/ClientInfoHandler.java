package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientInfoHandler {

    private final ConcurrentHashMap<String, String> clientInfo = new ConcurrentHashMap<>();

    public void setClientInfo(String name, String value) throws SQLException {
        if (value == null) {
            clientInfo.remove(name);
        } else {
            clientInfo.put(name, value);
        }
    }

    public void setClientInfo(Properties properties) throws SQLException {
        clientInfo.clear();
        for (var entry : properties.entrySet()) {
            clientInfo.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    public String getClientInfo(String name) throws SQLException {
        return clientInfo.get(name);
    }

    public Properties getClientInfo() throws SQLException {
        var properties = new Properties();
        properties.putAll(clientInfo);
        return properties;
    }
}

