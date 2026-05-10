package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcQueryCaptureListenerTest {

    private static ResultSet proxyViaFactory(JdbcQueryCaptureListener listener, ResultSet rawRs) {
        var logic = listener.resultSetProxyLogicFactory().create(rawRs, null, null);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (InvocationHandler) logic
        );
    }

    @Test
    @DisplayName("Given a successful execution, when afterQuery is called, then it captures SQL, parameters and timing")
    void shouldCaptureExecutedQueryWithParameters() throws Exception {
        var listener = new JdbcQueryCaptureListener();
        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setElapsedTime(12L);

        var queryInfo = new QueryInfo("SELECT * FROM users WHERE id = ? AND active = ?");
        var setIntMethod = PreparedStatement.class.getMethod("setInt", int.class, int.class);
        var setBooleanMethod = PreparedStatement.class.getMethod("setBoolean", int.class, boolean.class);
        queryInfo.setParametersList(List.of(List.of(
                new ParameterSetOperation(setIntMethod, new Object[]{1, 7}),
                new ParameterSetOperation(setBooleanMethod, new Object[]{2, true})
        )));

        listener.afterQuery(executionInfo, List.of(queryInfo));

        assertEquals(1, listener.events().size());
        var event = listener.events().get(0);
        assertEquals("default-datasource", event.datasourceId());
        assertEquals("SELECT * FROM users WHERE id = ? AND active = ?", event.sql());
        assertEquals(List.of(List.of(7, true)), event.parameters());
        assertEquals(12L, event.elapsedTimeMillis());
        assertTrue(event.success());
        assertNull(event.error());

        listener.close();
    }

    @Test
    @DisplayName("Given a failing execution and an event consumer, when afterQuery is called, then it stores and publishes the failure")
    void shouldCaptureAndPublishFailureEvent() throws Exception {
        var publishedEvent = new AtomicReference<JdbcQueryInterceptedEvent>();
        var listener = new JdbcQueryCaptureListener(publishedEvent::set);

        var failure = new IllegalStateException("boom");
        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(false);
        executionInfo.setThrowable(failure);
        executionInfo.setElapsedTime(5L);

        listener.afterQuery(executionInfo, List.of(new QueryInfo("UPDATE users SET active = false")));

        assertEquals(1, listener.events().size());
        var event = listener.events().get(0);
        assertEquals("default-datasource", event.datasourceId());
        assertEquals("UPDATE users SET active = false", event.sql());
        assertEquals(5L, event.elapsedTimeMillis());
        assertEquals(failure, event.error());
        assertEquals(event, publishedEvent.get());

        listener.close();
    }

    @Test
    @DisplayName("Given null or empty query info list, when afterQuery is called, then it ignores the callback")
    void shouldIgnoreEmptyCallbacks() {
        var listener = new JdbcQueryCaptureListener();

        listener.afterQuery(new ExecutionInfo(), null);
        listener.afterQuery(new ExecutionInfo(), List.of());
        listener.beforeQuery(new ExecutionInfo(), List.of());

        assertNotNull(listener.events());
        assertTrue(listener.events().isEmpty());

        listener.close();
    }

    @Test
    @DisplayName("Given a query returning ResultSet, when factory-proxied result is consumed, then listener emits protobuf event with consumed rows")
    void shouldPublishProtoEventAfterResultSetConsumption() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var rawRs = usersRowSet();
        var queryInfo = new QueryInfo("SELECT id, name FROM users WHERE active = ?");
        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(rawRs);

        // Simulate datasource-proxy: beforeQuery → factory.create → afterQuery → caller iterates RS
        listener.beforeQuery(executionInfo, List.of(queryInfo));
        try (var wrappedRs = proxyViaFactory(listener, rawRs)) {
            listener.afterQuery(executionInfo, List.of(queryInfo));
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
        assertEquals("SELECT id, name FROM users WHERE active = ?", protoEvent.get().getSimpleStatement().getSql());

        listener.close();
    }

    @Test
    @DisplayName("Given an update execution, when afterQuery is called, then listener emits protobuf event with update_count row")
    void shouldPublishProtoEventForUpdateCount() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(3);

        listener.afterQuery(executionInfo, List.of(new QueryInfo("UPDATE users SET active = false")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals("default-datasource", protoEvent.get().getDatasourceId());
        assertEquals(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS, protoEvent.get().getStatus());
        assertEquals(1, protoEvent.get().getResultSet().getRowsCount());
        assertEquals(3L, protoEvent.get().getResultSet().getRows(0).getValues(0).getLongVal());
        assertEquals(3L, protoEvent.get().getUpdateCount());

        listener.close();
    }

    @Test
    @DisplayName("Given a failing query execution, when proto event is emitted, then it includes error type and message")
    void shouldIncludeErrorPayloadInProtoEventWhenExecutionFails() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(false);
        executionInfo.setResult(0);
        executionInfo.setThrowable(new IllegalStateException("boom-query"));

        listener.afterQuery(executionInfo, List.of(new QueryInfo("SELECT * FROM users")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR, protoEvent.get().getStatus());
        assertTrue(protoEvent.get().hasError());
        assertEquals(IllegalStateException.class.getName(), protoEvent.get().getError().getType());
        assertEquals("boom-query", protoEvent.get().getError().getMessage());

        listener.close();
    }

    private static java.sql.ResultSet usersRowSet() throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "id");
        metadata.setColumnLabel(1, "id");
        metadata.setColumnType(1, Types.BIGINT);
        metadata.setColumnTypeName(1, "BIGINT");
        metadata.setColumnName(2, "name");
        metadata.setColumnLabel(2, "name");
        metadata.setColumnType(2, Types.VARCHAR);
        metadata.setColumnTypeName(2, "VARCHAR");

        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);

        rowSet.moveToInsertRow();
        rowSet.updateLong(1, 1L);
        rowSet.updateString(2, "alice");
        rowSet.insertRow();

        rowSet.moveToInsertRow();
        rowSet.updateLong(1, 2L);
        rowSet.updateString(2, "bob");
        rowSet.insertRow();

        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        return rowSet;
    }

    @Test
    @DisplayName("Given small rowBatchSize, when consuming ResultSet via factory proxy, then listener emits multiple protobuf chunks")
    void shouldEmitMultipleChunksWhenRowBatchSizeIsSmall() throws Exception {
        var chunks = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(2);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    chunks.add(mockedQuery);
                    latch.countDown();
                },
                new AsyncDispatchConfig(64, 1, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 1)
        );

        var rawRs = usersRowSet();
        var queryInfo = new QueryInfo("SELECT id, name FROM users");
        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(rawRs);

        listener.beforeQuery(executionInfo, List.of(queryInfo));
        try (var wrappedRs = proxyViaFactory(listener, rawRs)) {
            listener.afterQuery(executionInfo, List.of(queryInfo));
            while (wrappedRs.next()) {
                // consume all rows
            }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).getResultSet().getRowsCount());
        assertEquals(1, chunks.get(1).getResultSet().getRowsCount());

        listener.close();
    }

    @Test
    @DisplayName("Given query parameters, when proto event is emitted, then statement type is prepared and parameter metadata is serialized")
    void shouldEmitPreparedStatementWithParameterMetadata() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(1);

        var queryInfo = new QueryInfo("SELECT * FROM users WHERE id = ? AND active = ?");
        var setLongMethod = PreparedStatement.class.getMethod("setLong", int.class, long.class);
        var setBooleanMethod = PreparedStatement.class.getMethod("setBoolean", int.class, boolean.class);
        queryInfo.setParametersList(List.of(List.of(
                new ParameterSetOperation(setLongMethod, new Object[]{1, 10L}),
                new ParameterSetOperation(setBooleanMethod, new Object[]{2, true})
        )));

        listener.afterQuery(executionInfo, List.of(queryInfo));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasPreparedStatement());
        assertEquals(2, protoEvent.get().getPreparedStatement().getParametersCount());
        assertEquals(1, protoEvent.get().getPreparedStatement().getParameters(0).getIndex());
        assertEquals(10L, protoEvent.get().getPreparedStatement().getParameters(0).getValue().getLongVal());
        assertEquals(2, protoEvent.get().getPreparedStatement().getParameters(1).getIndex());
        assertTrue(protoEvent.get().getPreparedStatement().getParameters(1).getValue().getBoolVal());

        listener.close();
    }

    @Test
    @DisplayName("Given callable SQL format, when proto event is emitted, then statement type is callable")
    void shouldEmitCallableStatementForCallableSql() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(0);

        listener.afterQuery(executionInfo, List.of(new QueryInfo("{ call my_proc(?) }")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasCallableStatement());
        assertEquals("{ call my_proc(?) }", protoEvent.get().getCallableStatement().getCallSql());

        listener.close();
    }

    @Test
    @DisplayName("Given empty result set consumption, when factory-proxied ResultSet is closed without rows, then proto event keeps metadata and zero rows")
    void shouldEmitMetadataWithNoRowsWhenResultSetIsEmpty() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var rawRs = emptyUsersRowSet();
        var queryInfo = new QueryInfo("SELECT id, name FROM users WHERE 1 = 0");
        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(rawRs);

        listener.beforeQuery(executionInfo, List.of(queryInfo));
        try (var wrappedRs = proxyViaFactory(listener, rawRs)) {
            listener.afterQuery(executionInfo, List.of(queryInfo));
            assertFalse(wrappedRs.next());
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(2, protoEvent.get().getResultSet().getMetadataCount());
        assertEquals(0, protoEvent.get().getResultSet().getRowsCount());

        listener.close();
    }

    private static java.sql.ResultSet emptyUsersRowSet() throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "id");
        metadata.setColumnLabel(1, "id");
        metadata.setColumnType(1, Types.BIGINT);
        metadata.setColumnTypeName(1, "BIGINT");
        metadata.setColumnName(2, "name");
        metadata.setColumnLabel(2, "name");
        metadata.setColumnType(2, Types.VARCHAR);
        metadata.setColumnTypeName(2, "VARCHAR");

        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.beforeFirst();
        return rowSet;
    }

    @Test
    @DisplayName("Given explicit datasource id, when event is captured, then datasource id is preserved in local and proto event")
    void shouldUseExplicitDatasourceId() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var listener = new JdbcQueryCaptureListener(
                "users-primary",
                event -> {},
                mockedQuery -> {
                    protoEvent.set(mockedQuery);
                    latch.countDown();
                }
        );

        var executionInfo = new ExecutionInfo();
        executionInfo.setSuccess(true);
        executionInfo.setResult(1);

        listener.afterQuery(executionInfo, List.of(new QueryInfo("UPDATE users SET active = true")));

        assertEquals(1, listener.events().size());
        assertEquals("users-primary", listener.events().get(0).datasourceId());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("users-primary", protoEvent.get().getDatasourceId());

        listener.close();
    }
}
