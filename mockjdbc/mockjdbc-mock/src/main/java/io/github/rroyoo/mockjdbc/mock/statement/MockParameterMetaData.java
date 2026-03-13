package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.util.List;

final class MockParameterMetaData implements ParameterMetaData {

    private final List<ParameterMetadata> parameters;

    MockParameterMetaData(List<ParameterMetadata> parameters) {
        this.parameters = parameters;
    }

    @Override
    public int getParameterCount() throws SQLException {
        return parameters.size();
    }

    @Override
    public int isNullable(int param) throws SQLException {
        return ParameterMetaData.parameterNullableUnknown;
    }

    @Override
    public boolean isSigned(int param) throws SQLException {
        return true;
    }

    @Override
    public int getPrecision(int param) throws SQLException {
        return 0;
    }

    @Override
    public int getScale(int param) throws SQLException {
        return 0;
    }

    @Override
    public int getParameterType(int param) throws SQLException {
        return parameters.get(param - 1).getSqlType();
    }

    @Override
    public String getParameterTypeName(int param) throws SQLException {
        return parameters.get(param - 1).getTypeName();
    }

    @Override
    public String getParameterClassName(int param) throws SQLException {
        return Object.class.getName();
    }

    @Override
    public int getParameterMode(int param) throws SQLException {
        return ParameterMetaData.parameterModeIn;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}

