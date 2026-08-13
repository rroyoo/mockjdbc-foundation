package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExecutionCaptureTest {

    // ── captureUpdate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given a successful update execution, when captureUpdate is called, then it stores an event with SQL and timing")
    void shouldStoreUpdateEventWithSqlAndTiming() {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        capture.captureUpdate("UPDATE users SET active = false", List.of(), 3L, 12L, true, null);

        assertEquals(1, capture.events().size());
        var event = capture.events().get(0);
        assertEquals("default-datasource", event.datasourceId());
        assertEquals("UPDATE users SET active = false", event.sql());
        assertEquals(12L, event.elapsedTimeMillis());
        assertTrue(event.success());
        assertNull(event.error());

        capture.close();
    }

    @Test
    @DisplayName("Given parameters in captureUpdate, when event is stored, then parameters are wrapped in a single group")
    void shouldWrapParamsInSingleGroup() {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        capture.captureUpdate("SELECT * FROM users WHERE id = ? AND active = ?",
                List.of(7, true), 0L, 12L, true, null);

        assertEquals(1, capture.events().size());
        assertEquals(List.of(List.of(7, true)), capture.events().get(0).parameters());

        capture.close();
    }

    @Test
    @DisplayName("Given a failing execution, when captureUpdate is called, then event stores the error and success=false")
    void shouldStoreFailureEventWithError() {
        var publishedEvent = new AtomicReference<JdbcQueryInterceptedEvent>();
        var capture = new JdbcExecutionCapture("default-datasource",
                publishedEvent::set,
                MockedQueryEventProducer.fromConsumer(q -> {}),
                AsyncDispatchConfig.defaults());

        var failure = new IllegalStateException("boom");
        capture.captureUpdate("UPDATE users SET active = false", List.of(), 0L, 5L, false, failure);

        assertEquals(1, capture.events().size());
        var event = capture.events().get(0);
        assertFalse(event.success());
        assertEquals(failure, event.error());
        assertEquals(event, publishedEvent.get());

        capture.close();
    }

    // ── Proto events — update path ────────────────────────────────────────────

    @Test
    @DisplayName("Given a successful update, when proto event is emitted, then it contains update_count and datasource id")
    void shouldPublishProtoEventForUpdateCount() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        capture.captureUpdate("UPDATE users SET active = false", List.of(), 3L, 0L, true, null);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals("default-datasource", protoEvent.get().getDatasourceId());
        assertEquals(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS, protoEvent.get().getStatus());
        assertEquals(1, protoEvent.get().getResultSet().getRowsCount());
        assertEquals(3L, protoEvent.get().getResultSet().getRows(0).getValues(0).getLongVal());
        assertEquals(3L, protoEvent.get().getUpdateCount());

        capture.close();
    }

    @Test
    @DisplayName("Given a failing execution, when proto event is emitted, then it contains error type and message")
    void shouldIncludeErrorPayloadInProtoEventWhenExecutionFails() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        capture.captureUpdate("SELECT * FROM users", List.of(), 0L, 0L, false,
                new IllegalStateException("boom-query"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR, protoEvent.get().getStatus());
        assertTrue(protoEvent.get().hasError());
        assertEquals(IllegalStateException.class.getName(), protoEvent.get().getError().getType());
        assertEquals("boom-query", protoEvent.get().getError().getMessage());

        capture.close();
    }

    @Test
    @DisplayName("Given parameters, when proto event is emitted, then statement type is prepared with parameter metadata")
    void shouldEmitPreparedStatementWithParameterMetadata() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        capture.captureUpdate("SELECT * FROM users WHERE id = ? AND active = ?",
                List.of(10L, true), 0L, 0L, true, null);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasPreparedStatement());
        assertEquals(2, protoEvent.get().getPreparedStatement().getParametersCount());
        assertEquals(1, protoEvent.get().getPreparedStatement().getParameters(0).getIndex());
        assertEquals(10L, protoEvent.get().getPreparedStatement().getParameters(0).getValue().getLongVal());
        assertEquals(2, protoEvent.get().getPreparedStatement().getParameters(1).getIndex());
        assertTrue(protoEvent.get().getPreparedStatement().getParameters(1).getValue().getBoolVal());

        capture.close();
    }

    @Test
    @DisplayName("Given callable SQL format, when proto event is emitted, then statement type is callable")
    void shouldEmitCallableStatementForCallableSql() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        capture.captureUpdate("{ call my_proc(?) }", List.of(), 0L, 0L, true, null);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasCallableStatement());
        assertEquals("{ call my_proc(?) }", protoEvent.get().getCallableStatement().getCallSql());

        capture.close();
    }

    // ── Proto events — SELECT / ResultSet path ────────────────────────────────

    @Test
    @DisplayName("Given a wrapped ResultSet consumed via wrapResultSet, then proto event contains rows and metadata")
    void shouldPublishProtoEventAfterResultSetConsumption() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var rawRs = usersRowSet();
        var startMs = System.currentTimeMillis();
        try (var wrappedRs = capture.wrapResultSet("SELECT id, name FROM users WHERE active = ?",
                List.of(true), rawRs, startMs)) {
            assertTrue(wrappedRs.next());
            assertTrue(wrappedRs.next());
            assertFalse(wrappedRs.next());
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(2, protoEvent.get().getResultSet().getRowsCount());
        assertEquals(2, protoEvent.get().getResultSet().getMetadataCount());
        assertEquals("default-datasource", protoEvent.get().getDatasourceId());
        assertEquals(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS, protoEvent.get().getStatus());
        assertEquals(2L, protoEvent.get().getRowCount());
        // Parameters present → PreparedStatement type
        assertTrue(protoEvent.get().hasPreparedStatement());
        assertEquals("SELECT id, name FROM users WHERE active = ?",
                protoEvent.get().getPreparedStatement().getSql());

        capture.close();
    }

    @Test
    @DisplayName("Given small rowBatchSize, when consuming wrapped ResultSet, then multiple proto chunks are emitted")
    void shouldEmitMultipleChunksWhenRowBatchSizeIsSmall() throws Exception {
        var chunks = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(2);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { chunks.add(q); latch.countDown(); }),
                new AsyncDispatchConfig(64, 1, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 1));

        var rawRs = usersRowSet();
        var startMs = System.currentTimeMillis();
        try (var wrappedRs = capture.wrapResultSet("SELECT id, name FROM users", List.of(), rawRs, startMs)) {
            while (wrappedRs.next()) { /* consume all */ }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).getResultSet().getRowsCount());
        assertEquals(1, chunks.get(1).getResultSet().getRowsCount());

        capture.close();
    }

    @Test
    @DisplayName("Given empty ResultSet, when wrapped RS is closed without rows, then proto event preserves metadata with zero rows")
    void shouldEmitMetadataWithNoRowsWhenResultSetIsEmpty() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var rawRs = emptyUsersRowSet();
        var startMs = System.currentTimeMillis();
        try (var wrappedRs = capture.wrapResultSet("SELECT id, name FROM users WHERE 1 = 0",
                List.of(), rawRs, startMs)) {
            assertFalse(wrappedRs.next());
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(2, protoEvent.get().getResultSet().getMetadataCount());
        assertEquals(0, protoEvent.get().getResultSet().getRowsCount());

        capture.close();
    }

    // ── Datasource identity ───────────────────────────────────────────────────

    @Test
    @DisplayName("Given explicit datasource id, when events are captured, then datasource id is preserved in local and proto events")
    void shouldUseExplicitDatasourceId() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("users-primary", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        capture.captureUpdate("UPDATE users SET active = true", List.of(), 1L, 0L, true, null);

        assertEquals(1, capture.events().size());
        assertEquals("users-primary", capture.events().get(0).datasourceId());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("users-primary", protoEvent.get().getDatasourceId());

        capture.close();
    }

    // ── clear() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given captured events, when clear is called, then event buffer is empty")
    void shouldClearEventBuffer() {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        capture.captureUpdate("DELETE FROM audit_log", List.of(), 10L, 0L, true, null);
        assertEquals(1, capture.events().size());

        capture.clear();
        assertTrue(capture.events().isEmpty());

        capture.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResultSet usersRowSet() throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "id");   metadata.setColumnLabel(1, "id");
        metadata.setColumnType(1, Types.BIGINT); metadata.setColumnTypeName(1, "BIGINT");
        metadata.setColumnName(2, "name"); metadata.setColumnLabel(2, "name");
        metadata.setColumnType(2, Types.VARCHAR); metadata.setColumnTypeName(2, "VARCHAR");

        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);

        rowSet.moveToInsertRow(); rowSet.updateLong(1, 1L); rowSet.updateString(2, "alice"); rowSet.insertRow();
        rowSet.moveToInsertRow(); rowSet.updateLong(1, 2L); rowSet.updateString(2, "bob");   rowSet.insertRow();
        rowSet.moveToCurrentRow(); rowSet.beforeFirst();
        return rowSet;
    }

    private static ResultSet emptyUsersRowSet() throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "id");   metadata.setColumnLabel(1, "id");
        metadata.setColumnType(1, Types.BIGINT); metadata.setColumnTypeName(1, "BIGINT");
        metadata.setColumnName(2, "name"); metadata.setColumnLabel(2, "name");
        metadata.setColumnType(2, Types.VARCHAR); metadata.setColumnTypeName(2, "VARCHAR");

        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.beforeFirst();
        return rowSet;
    }
}
