package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockParameterMetaDataTest {

    @Test
    @DisplayName("Given parameter metadata entries, when querying ParameterMetaData, then it exposes count, type and mode information")
    void shouldExposeParameterMetadataDetails() throws Exception {
        var parameters = List.of(
                parameter(1, Types.VARCHAR, "VARCHAR"),
                parameter(2, Types.INTEGER, "INTEGER")
        );
        var metaData = new MockParameterMetaData(parameters);

        assertEquals(2, metaData.getParameterCount());
        assertEquals(ParameterMetaData.parameterNullableUnknown, metaData.isNullable(1));
        assertTrue(metaData.isSigned(1));
        assertEquals(0, metaData.getPrecision(1));
        assertEquals(0, metaData.getScale(1));
        assertEquals(Types.VARCHAR, metaData.getParameterType(1));
        assertEquals("VARCHAR", metaData.getParameterTypeName(1));
        assertEquals(Object.class.getName(), metaData.getParameterClassName(1));
        assertEquals(ParameterMetaData.parameterModeIn, metaData.getParameterMode(1));
        assertEquals(Types.INTEGER, metaData.getParameterType(2));
    }

    @Test
    @DisplayName("Given MockParameterMetaData, when checking wrapper methods, then isWrapperFor and unwrap behave consistently")
    void shouldSupportJdbcWrapperContract() throws Exception {
        var metaData = new MockParameterMetaData(List.of(parameter(1, Types.VARCHAR, "VARCHAR")));

        assertTrue(metaData.isWrapperFor(MockParameterMetaData.class));
        assertFalse(metaData.isWrapperFor(String.class));
        assertEquals(metaData, metaData.unwrap(MockParameterMetaData.class));
        assertThrows(SQLException.class, () -> metaData.unwrap(String.class));
    }

    private static ParameterMetadata parameter(int index, int sqlType, String typeName) {
        return ParameterMetadata.newBuilder()
                .setIndex(index)
                .setSqlType(sqlType)
                .setTypeName(typeName)
                .setValue(JdbcValue.newBuilder().setStringVal("x").build())
                .build();
    }
}

