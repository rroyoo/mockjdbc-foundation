package io.github.rroyoo.mockjdbc.mock.resultset;

import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.sql.ResultSet;

/**
 * Converts a protobuf SerializedResultSet into a JDBC ResultSet implementation.
 *
 * This converter transforms the gRPC response (MockedQuery.result_set) into
 * a navigable ResultSet that can be used by JDBC clients.
 */
public final class MockResultSetConverter {

    private MockResultSetConverter() {
        // Utility class
    }

    /**
     * Convert a SerializedResultSet from protobuf to a JDBC ResultSet.
     *
     * @param serializedResultSet the protobuf result set from gRPC response
     * @return a ResultSet implementation backed by the serialized data
     */
    public static ResultSet convert(SerializedResultSet serializedResultSet) {
        return new MockResultSet(serializedResultSet);
    }
}

