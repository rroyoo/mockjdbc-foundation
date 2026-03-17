package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicReference;

final class GeneratedKeysHandler {

    private final AtomicReference<ResultSet> generatedKeys = new AtomicReference<>(null);

    public void clearGeneratedKeys() throws SQLException {
        closeCurrent();
    }

    public void storeGeneratedKeys(SerializedResultSet serializedResultSet) throws SQLException {
        closeCurrent();
        generatedKeys.set(ResultSetFactory.create(serializedResultSet));
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        var current = generatedKeys.get();
        if (current == null) {
            return ResultSetFactory.create(emptyGeneratedKeysResultSet());
        }
        return current;
    }

    private static SerializedResultSet emptyGeneratedKeysResultSet() {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("GENERATED_KEY")
                        .setLabel("GENERATED_KEY")
                        .setSqlType(Types.BIGINT)
                        .setTypeName("BIGINT")
                        .build())
                .build();
    }

    private void closeCurrent() throws SQLException {
        var current = generatedKeys.getAndSet(null);
        if (current != null) {
            try {
                current.close();
            } catch (SQLException ignored) {
                // Ignore close failures for mock result set implementations.
            }
        }
    }
}

