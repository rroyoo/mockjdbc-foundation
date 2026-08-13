package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ByteBuddyResultSetWrapperFactory {

    private final int rowBatchSize;

    ByteBuddyResultSetWrapperFactory(int rowBatchSize) {
        this.rowBatchSize = rowBatchSize;
    }

    int rowBatchSize() {
        return rowBatchSize;
    }

    /**
     * Wraps the given ResultSet in a JDK proxy that captures rows during iteration and emits a
     * {@link SerializedResultSet} to {@code onConsumed} when the ResultSet is exhausted or closed.
     */
    ResultSet wrap(ResultSet delegate, Consumer<SerializedResultSet> onConsumed) throws SQLException {
        try {
            return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                    delegate.getClass().getClassLoader(),
                    new Class<?>[]{ ResultSet.class },
                    new ConsumptionTrackingInvocationHandler(delegate, onConsumed, rowBatchSize)
            );
        } catch (Exception e) {
            throw new SQLException("Failed to create ResultSet wrapper proxy", e);
        }
    }

    private static final class ConsumptionTrackingInvocationHandler implements InvocationHandler {

        private final ResultSet delegate;
        private final Consumer<SerializedResultSet> onConsumed;
        private final int rowBatchSize;
        private final List<ColumnMetadata> metadata = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private boolean metadataInitialized;
        private boolean published;
        private boolean emittedChunks;

        private ConsumptionTrackingInvocationHandler(ResultSet delegate,
                                                     Consumer<SerializedResultSet> onConsumed,
                                                     int rowBatchSize) {
            this.delegate = delegate;
            this.onConsumed = onConsumed;
            this.rowBatchSize = rowBatchSize;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            var methodName = method.getName();

            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return "CapturingResultSet{" + delegate + "}";
            }

            try {
                var result = method.invoke(delegate, args);

                if ("next".equals(methodName)) {
                    if (Boolean.TRUE.equals(result)) {
                        captureCurrentRow();
                    } else {
                        publishIfNeeded();
                    }
                } else if ("close".equals(methodName)) {
                    publishIfNeeded();
                }

                return result;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        private void captureCurrentRow() throws SQLException {
            initializeMetadataIfNeeded();
            if (metadata.isEmpty()) {
                return;
            }

            var rowBuilder = Row.newBuilder();
            for (var index = 1; index <= metadata.size(); index++) {
                var value = delegate.getObject(index);
                rowBuilder.addValues(toJdbcValue(value));
            }
            rows.add(rowBuilder.build());

            if (rows.size() >= rowBatchSize) {
                publishChunk();
            }
        }

        private void initializeMetadataIfNeeded() throws SQLException {
            if (metadataInitialized) {
                return;
            }

            metadataInitialized = true;
            var resultSetMetaData = delegate.getMetaData();
            if (resultSetMetaData == null) {
                return;
            }

            for (var index = 1; index <= resultSetMetaData.getColumnCount(); index++) {
                metadata.add(toColumnMetadata(resultSetMetaData, index));
            }
        }

        private void publishIfNeeded() throws SQLException {
            if (published) {
                return;
            }

            published = true;
            initializeMetadataIfNeeded();
            if (!rows.isEmpty()) {
                publishChunk();
                return;
            }

            if (!emittedChunks) {
                onConsumed.accept(SerializedResultSet.newBuilder().addAllMetadata(metadata).build());
            }
        }

        private void publishChunk() {
            var resultSet = SerializedResultSet.newBuilder()
                    .addAllMetadata(metadata)
                    .addAllRows(rows)
                    .build();
            onConsumed.accept(resultSet);
            rows.clear();
            emittedChunks = true;
        }

        private static ColumnMetadata toColumnMetadata(ResultSetMetaData metaData, int columnIndex) throws SQLException {
            return ColumnMetadata.newBuilder()
                    .setName(metaData.getColumnName(columnIndex))
                    .setLabel(metaData.getColumnLabel(columnIndex))
                    .setSqlType(metaData.getColumnType(columnIndex))
                    .setTypeName(metaData.getColumnTypeName(columnIndex))
                    .build();
        }

    }

    static JdbcValue toJdbcValue(Object value) {
        if (value == null) {
            return JdbcValue.newBuilder().setIsNull(true).build();
        }
        if (value instanceof byte[] bytes) {
            return JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(bytes)).build();
        }
        if (value instanceof String stringValue) {
            return JdbcValue.newBuilder().setStringVal(stringValue).build();
        }
        if (value instanceof Boolean boolValue) {
            return JdbcValue.newBuilder().setBoolVal(boolValue).build();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return JdbcValue.newBuilder().setLongVal(((Number) value).longValue()).build();
        }
        if (value instanceof Float || value instanceof Double) {
            return JdbcValue.newBuilder().setDoubleVal(((Number) value).doubleValue()).build();
        }
        if (value instanceof BigDecimal decimalValue) {
            return JdbcValue.newBuilder().setDecimalVal(decimalValue.toPlainString()).build();
        }
        if (value instanceof Timestamp timestampValue) {
            return JdbcValue.newBuilder().setTimestampVal(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(timestampValue.toInstant().getEpochSecond())
                    .setNanos(timestampValue.toInstant().getNano())
                    .build()).build();
        }
        if (value instanceof java.util.Date dateValue) {
            return JdbcValue.newBuilder().setStringVal(new Timestamp(dateValue.getTime()).toString()).build();
        }

        return JdbcValue.newBuilder().setStringVal(String.valueOf(value)).build();
    }

    static SerializedResultSet resultSetFromUpdateResult(Object result) {
        var metadata = ColumnMetadata.newBuilder()
                .setName("update_count")
                .setLabel("update_count")
                .setSqlType(Types.BIGINT)
                .setTypeName("BIGINT")
                .build();

        var builder = SerializedResultSet.newBuilder().addMetadata(metadata);
        if (result instanceof Number number) {
            var row = Row.newBuilder()
                    .addValues(JdbcValue.newBuilder().setLongVal(number.longValue()).build())
                    .build();
            builder.addRows(row);
        }

        return builder.build();
    }
}
