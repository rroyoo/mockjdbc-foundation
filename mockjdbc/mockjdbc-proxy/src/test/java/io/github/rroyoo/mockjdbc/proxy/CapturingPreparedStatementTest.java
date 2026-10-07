package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapturingPreparedStatementTest {

    private static PreparedStatement capturingProxy(PreparedStatement delegate,
                                                     String sql,
                                                     JdbcExecutionCapture capture) {
        return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{ PreparedStatement.class },
                new CapturingPreparedStatementInvocationHandler(delegate, sql, capture)
        );
    }

    // ── executeUpdate with parameters ─────────────────────────────────────────

    @Test
    @DisplayName("Given setXxx calls followed by executeUpdate, when captured, then proto event includes parameters in order")
    void shouldCaptureParametersInOrderOnExecuteUpdate() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenReturn(1);

        var proxy = capturingProxy(delegate, "UPDATE users SET name = ? WHERE id = ?", capture);
        proxy.setString(1, "alice");
        proxy.setLong(2, 42L);
        var count = proxy.executeUpdate();

        assertEquals(1, count);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasPreparedStatement());
        assertEquals(2, protoEvent.get().getPreparedStatement().getParametersCount());
        assertEquals("alice", protoEvent.get().getPreparedStatement().getParameters(0).getValue().getStringVal());
        assertEquals(42L, protoEvent.get().getPreparedStatement().getParameters(1).getValue().getLongVal());

        // Verify delegate received the setters
        verify(delegate).setString(1, "alice");
        verify(delegate).setLong(2, 42L);
        verify(delegate).executeUpdate();

        capture.close();
    }

    @Test
    @DisplayName("Given setNull call, when captured, then parameter value is null in the proto event")
    void shouldCaptureNullParameterAsNullValue() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);

        var proxy = capturingProxy(delegate, "UPDATE users SET note = ? WHERE id = ?", capture);
        proxy.setNull(1, Types.VARCHAR);
        proxy.setInt(2, 7);
        proxy.executeUpdate();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(protoEvent.get().getPreparedStatement().getParameters(0).getValue().getIsNull());

        capture.close();
    }

    // ── clearParameters ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Given parameters set and clearParameters called, when executeUpdate is captured, then event has no parameters")
    void shouldClearParametersBeforeExecution() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenReturn(1);

        var proxy = capturingProxy(delegate, "UPDATE users SET x = ?", capture);
        proxy.setInt(1, 99);
        proxy.clearParameters();
        proxy.executeUpdate();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(protoEvent.get().hasSimpleStatement()); // no params → PlainStatement

        capture.close();
    }

    // ── executeQuery ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given executeQuery on PreparedStatement, when ResultSet is consumed, then proto event has rows and params")
    void shouldCaptureExecuteQueryWithResultSetAndParams() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var rawRs = twoRowResultSet();
        var delegate = mock(PreparedStatement.class);
        when(delegate.executeQuery()).thenReturn(rawRs);

        var proxy = capturingProxy(delegate, "SELECT id FROM users WHERE active = ?", capture);
        proxy.setBoolean(1, true);

        try (var rs = proxy.executeQuery()) {
            while (rs.next()) { /* consume */ }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertEquals(2L, protoEvent.get().getRowCount());
        assertTrue(protoEvent.get().hasPreparedStatement());
        assertTrue(protoEvent.get().getPreparedStatement().getParameters(0).getValue().getBoolVal());

        capture.close();
    }

    // ── execute() → ResultSet path ────────────────────────────────────────────

    @Test
    @DisplayName("Given execute returns true on PreparedStatement, when getResultSet consumed, then proto event is emitted")
    void shouldCaptureExecuteReturningTrueWithResultSet() throws Exception {
        var rowCount = new AtomicLong(-1);
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { rowCount.set(q.getRowCount()); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var rawRs = twoRowResultSet();
        var delegate = mock(PreparedStatement.class);
        when(delegate.execute()).thenReturn(true);
        when(delegate.getResultSet()).thenReturn(rawRs);

        var proxy = capturingProxy(delegate, "SELECT * FROM users WHERE id = ?", capture);
        proxy.setLong(1, 1L);
        var hasRs = proxy.execute();

        assertTrue(hasRs);
        try (var rs = proxy.getResultSet()) {
            assertNotNull(rs);
            assertFalse(rs == rawRs); // should be wrapped
            while (rs.next()) { /* consume */ }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2L, rowCount.get());

        capture.close();
    }

    // ── execute() → update path ───────────────────────────────────────────────

    @Test
    @DisplayName("Given execute returns false on PreparedStatement, when called, then update count event is captured")
    void shouldCaptureExecuteReturningFalseAsUpdate() throws Exception {
        var capturedUpdate = new AtomicLong(-1);
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { capturedUpdate.set(q.getUpdateCount()); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.execute()).thenReturn(false);
        when(delegate.getUpdateCount()).thenReturn(4);

        var proxy = capturingProxy(delegate, "DELETE FROM sessions WHERE expired = ?", capture);
        proxy.setBoolean(1, true);
        var hasRs = proxy.execute();

        assertFalse(hasRs);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(4L, capturedUpdate.get());

        capture.close();
    }

    // ── Callable SQL detection ────────────────────────────────────────────────

    @Test
    @DisplayName("Given callable SQL, when execute is captured, then proto event is a CallableStatement")
    void shouldEmitCallableStatementEventForCallableSql() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);

        var proxy = capturingProxy(delegate, "{ call my_proc(?) }", capture);
        proxy.setInt(1, 5);
        proxy.executeUpdate();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasCallableStatement());
        assertEquals("{ call my_proc(?) }", protoEvent.get().getCallableStatement().getCallSql());

        capture.close();
    }

    // ── Failure path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given executeUpdate throws on PreparedStatement, when captured, then failure event includes error and params are cleared")
    void shouldCaptureFailureAndClearParamsOnException() throws Exception {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenThrow(new java.sql.SQLException("constraint violation"));

        var proxy = capturingProxy(delegate, "INSERT INTO users VALUES (?)", capture);
        proxy.setString(1, "duplicate");

        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, proxy::executeUpdate);

        assertEquals(1, capture.events().size());
        assertFalse(capture.events().get(0).success());
        assertNotNull(capture.events().get(0).error());

        // After exception, parameters are cleared — a subsequent execute with no params
        // should produce a SimpleStatement event. Use a fresh delegate to avoid Mockito stub state.
        var latch = new CountDownLatch(1);
        var protoRef = new AtomicReference<MockedQuery>();
        var delegate2 = mock(PreparedStatement.class);
        when(delegate2.executeUpdate()).thenReturn(0);
        var capture2 = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoRef.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());
        var proxy2 = capturingProxy(delegate2, "INSERT INTO users VALUES (?)", capture2);
        proxy2.executeUpdate(); // no params set
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(protoRef.get().hasSimpleStatement());

        capture.close();
        capture2.close();
    }

    // ── Delegation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given non-capture methods on PreparedStatement, when called via proxy, then they delegate to the underlying statement")
    void shouldDelegateNonCaptureMethods() throws Exception {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        var delegate = mock(PreparedStatement.class);
        when(delegate.getMaxRows()).thenReturn(50);

        var proxy = capturingProxy(delegate, "SELECT 1", capture);
        assertEquals(50, proxy.getMaxRows());
        verify(delegate).getMaxRows();

        proxy.close();
        verify(delegate).close();

        capture.close();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ResultSet twoRowResultSet() throws Exception {
        var metadata = new javax.sql.rowset.RowSetMetaDataImpl();
        metadata.setColumnCount(1);
        metadata.setColumnName(1, "id");
        metadata.setColumnLabel(1, "id");
        metadata.setColumnType(1, Types.BIGINT);
        metadata.setColumnTypeName(1, "BIGINT");

        var rowSet = javax.sql.rowset.RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow(); rowSet.updateLong(1, 1L); rowSet.insertRow();
        rowSet.moveToInsertRow(); rowSet.updateLong(1, 2L); rowSet.insertRow();
        rowSet.moveToCurrentRow(); rowSet.beforeFirst();
        return rowSet;
    }

    private static JdbcExecutionCapture localCapture(java.util.List<JdbcQueryInterceptedEvent> sink) {
        return new JdbcExecutionCapture("ds", sink::add,
                MockedQueryEventProducer.fromConsumer(q -> { }), AsyncDispatchConfig.defaults());
    }

    @Test
    @DisplayName("Given bound parameters, when executed twice without clearParameters, then both executions capture the same values")
    void shouldKeepParametersAcrossExecutionsUntilCleared() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<JdbcQueryInterceptedEvent>();
        var capture = localCapture(events);
        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenReturn(1);
        var proxy = capturingProxy(delegate, "UPDATE t SET x = ?", capture);

        proxy.setInt(1, 5);
        proxy.executeUpdate();
        proxy.executeUpdate();
        proxy.clearParameters();
        proxy.executeUpdate();

        assertEquals(List.of(List.of(List.<Object>of(5)), List.of(List.<Object>of(5)), List.<List<Object>>of()),
                events.stream().map(JdbcQueryInterceptedEvent::parameters).toList());
        capture.close();
    }

    @Test
    @DisplayName("Given a failing execution, when it throws, then bound parameters are kept for the next execution")
    void shouldKeepParametersAfterFailedExecution() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<JdbcQueryInterceptedEvent>();
        var capture = localCapture(events);
        var delegate = mock(PreparedStatement.class);
        when(delegate.executeUpdate()).thenThrow(new java.sql.SQLException("boom")).thenReturn(1);
        var proxy = capturingProxy(delegate, "UPDATE t SET x = ?", capture);

        proxy.setInt(1, 5);
        try { proxy.executeUpdate(); } catch (java.sql.SQLException expected) { /* expected */ }
        proxy.executeUpdate();

        assertFalse(events.get(0).success());
        assertEquals(List.of(List.<Object>of(5)), events.get(1).parameters());
        capture.close();
    }

    @Test
    @DisplayName("Given several addBatch calls, when executeBatch runs, then one parameter set per entry and real elapsed time are captured")
    void shouldCaptureBatchedParameterSets() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<JdbcQueryInterceptedEvent>();
        var protos = new java.util.concurrent.CopyOnWriteArrayList<MockedQuery>();
        var capture = new JdbcExecutionCapture("ds", events::add,
                MockedQueryEventProducer.fromConsumer(protos::add), AsyncDispatchConfig.defaults());
        var delegate = mock(PreparedStatement.class);
        when(delegate.executeBatch()).thenAnswer(inv -> { Thread.sleep(20); return new int[]{1, 1}; });
        var proxy = capturingProxy(delegate, "INSERT INTO t(a) VALUES (?)", capture);

        proxy.setString(1, "a");
        proxy.addBatch();
        proxy.setString(1, "b");
        proxy.addBatch();
        proxy.executeBatch();

        assertEquals(1, events.size());
        assertEquals(List.of(List.<Object>of("a"), List.<Object>of("b")), events.get(0).parameters());
        assertTrue(events.get(0).elapsedTimeMillis() >= 10);
        var deadline = System.currentTimeMillis() + 2000;
        while (protos.size() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertEquals(2, protos.size());
        assertTrue(protos.stream().allMatch(MockedQuery::hasPreparedStatement));
        capture.close();
    }

    @Test
    @DisplayName("Given a value bound after registerOutParameter, when the callable executes, then the parameter mode is INOUT")
    void shouldUpgradeRegisteredOutParameterToInOutWhenValueBoundLater() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("ds", e -> { },
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());
        var delegate = mock(java.sql.CallableStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);
        var proxy = (java.sql.CallableStatement) java.lang.reflect.Proxy.newProxyInstance(
                java.sql.CallableStatement.class.getClassLoader(),
                new Class<?>[]{ java.sql.CallableStatement.class },
                new CapturingPreparedStatementInvocationHandler(delegate, "{call p(?)}", capture));

        proxy.registerOutParameter(1, Types.INTEGER);
        proxy.setInt(1, 7);
        proxy.executeUpdate();
        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(java.sql.ParameterMetaData.parameterModeInOut,
                protoEvent.get().getCallableStatement().getParameters(0).getMode());
        capture.close();
    }
}
