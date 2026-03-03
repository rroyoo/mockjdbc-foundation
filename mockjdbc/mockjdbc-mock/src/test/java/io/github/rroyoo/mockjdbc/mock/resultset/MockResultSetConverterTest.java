package io.github.rroyoo.mockjdbc.mock.resultset;

import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class MockResultSetConverterTest {

    @Test
    @DisplayName("should convert empty SerializedResultSet to empty ResultSet")
    void shouldConvertEmptyResultSet() throws SQLException {
        SerializedResultSet serialized = SerializedResultSet.newBuilder().build();

        ResultSet resultSet = MockResultSetConverter.convert(serialized);

        assertNotNull(resultSet);
        assertFalse(resultSet.next());
    }

    @Test
    @DisplayName("should convert SerializedResultSet with single row")
    void shouldConvertSingleRow() throws SQLException {
        SerializedResultSet serialized = SerializedResultSet.newBuilder()
                .addMetadata(columnMetadata("id", "ID", java.sql.Types.INTEGER, "INTEGER"))
                .addMetadata(columnMetadata("name", "NAME", java.sql.Types.VARCHAR, "VARCHAR"))
                .addRows(row(intValue(1), stringValue("John")))
                .build();

        ResultSet resultSet = MockResultSetConverter.convert(serialized);

        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt("id"));
        assertEquals("John", resultSet.getString("name"));
        assertFalse(resultSet.next());
    }

    @Test
    @DisplayName("should convert SerializedResultSet with multiple rows")
    void shouldConvertMultipleRows() throws SQLException {
        SerializedResultSet serialized = SerializedResultSet.newBuilder()
                .addMetadata(columnMetadata("id", "ID", java.sql.Types.INTEGER, "INTEGER"))
                .addMetadata(columnMetadata("name", "NAME", java.sql.Types.VARCHAR, "VARCHAR"))
                .addRows(row(intValue(1), stringValue("John")))
                .addRows(row(intValue(2), stringValue("Jane")))
                .addRows(row(intValue(3), stringValue("Bob")))
                .build();

        ResultSet resultSet = MockResultSetConverter.convert(serialized);

        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
        assertEquals("John", resultSet.getString(2));

        assertTrue(resultSet.next());
        assertEquals(2, resultSet.getInt("id"));
        assertEquals("Jane", resultSet.getString("name"));

        assertTrue(resultSet.next());
        assertEquals(3, resultSet.getInt(1));
        assertEquals("Bob", resultSet.getString(2));

        assertFalse(resultSet.next());
    }

    // Helper methods for building test data
    private io.github.rroyoo.mockjdbc.mock.ColumnMetadata columnMetadata(String name, String label, int sqlType, String typeName) {
        return io.github.rroyoo.mockjdbc.mock.ColumnMetadata.newBuilder()
                .setName(name)
                .setLabel(label)
                .setSqlType(sqlType)
                .setTypeName(typeName)
                .build();
    }

    private io.github.rroyoo.mockjdbc.mock.Row row(io.github.rroyoo.mockjdbc.mock.JdbcValue... values) {
        return io.github.rroyoo.mockjdbc.mock.Row.newBuilder()
                .addAllValues(java.util.Arrays.asList(values))
                .build();
    }

    private io.github.rroyoo.mockjdbc.mock.JdbcValue intValue(int value) {
        return io.github.rroyoo.mockjdbc.mock.JdbcValue.newBuilder()
                .setLongVal(value)
                .build();
    }

    private io.github.rroyoo.mockjdbc.mock.JdbcValue stringValue(String value) {
        return io.github.rroyoo.mockjdbc.mock.JdbcValue.newBuilder()
                .setStringVal(value)
                .build();
    }
}

