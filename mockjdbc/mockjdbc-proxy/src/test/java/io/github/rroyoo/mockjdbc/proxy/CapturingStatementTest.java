package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapturingStatementTest {

    private static Statement capturingProxy(Statement delegate, JdbcExecutionCapture capture) {
        return (Statement) java.lang.reflect.Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{ Statement.class },
                new CapturingStatementInvocationHandler(delegate, capture)
        );
    }

    // ── executeUpdate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given executeUpdate returns a count, when called via capturing proxy, then event stores SQL and update count")
    void shouldCaptureExecuteUpdateWithCount() throws Exception {
        var latch = new CountDownLatch(1);
        var capturedUpdateCount = new AtomicLong(-1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {
                    capturedUpdateCount.set(q.getUpdateCount());
                    latch.countDown();
                }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("UPDATE users SET active = false")).thenReturn(5);

        var proxy = capturingProxy(delegate, capture);
        var result = proxy.executeUpdate("UPDATE users SET active = false");

        assertEquals(5, result);
        assertEquals(1, capture.events().size());
        assertEquals("UPDATE users SET active = false", capture.events().get(0).sql());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(5L, capturedUpdateCount.get());

        capture.close();
    }

    @Test
    @DisplayName("Given executeUpdate throws, when called via capturing proxy, then failure event is captured and exception propagated")
    void shouldCaptureExecuteUpdateFailureAndPropagate() throws Exception {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());

        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("UPDATE bad_table SET x = 1"))
                .thenThrow(new java.sql.SQLException("table not found"));

        var proxy = capturingProxy(delegate, capture);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                java.sql.SQLException.class,
                () -> proxy.executeUpdate("UPDATE bad_table SET x = 1")
        );
        assertEquals("table not found", thrown.getMessage());
        assertEquals(1, capture.events().size());
        assertFalse(capture.events().get(0).success());
        assertEquals(thrown, capture.events().get(0).error());

        capture.close();
    }

    // ── executeQuery ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given executeQuery returns a ResultSet, when consuming it, then proto event has rows")
    void shouldCaptureExecuteQueryResultSet() throws Exception {
        var latch = new CountDownLatch(1);
        var rowCount = new AtomicLong(-1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {
                    rowCount.set(q.getRowCount());
                    latch.countDown();
                }),
                AsyncDispatchConfig.defaults());

        // Build a simple ResultSet mock via CachedRowSet
        var rawRs = twoRowResultSet();
        var delegate = mock(Statement.class);
        when(delegate.executeQuery("SELECT id FROM users")).thenReturn(rawRs);

        var proxy = capturingProxy(delegate, capture);
        try (var rs = proxy.executeQuery("SELECT id FROM users")) {
            while (rs.next()) { /* consume all */ }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2L, rowCount.get());

        capture.close();
    }

    // ── execute ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given execute returns false (update), when called via capturing proxy, then update count event is captured")
    void shouldCaptureExecuteAsUpdateWhenReturnsFalse() throws Exception {
        var latch = new CountDownLatch(1);
        var capturedUpdateCount = new AtomicLong(-1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {
                    capturedUpdateCount.set(q.getUpdateCount());
                    latch.countDown();
                }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(Statement.class);
        when(delegate.execute("DELETE FROM sessions")).thenReturn(false);
        when(delegate.getUpdateCount()).thenReturn(3);

        var proxy = capturingProxy(delegate, capture);
        var hasRs = proxy.execute("DELETE FROM sessions");

        assertFalse(hasRs);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(3L, capturedUpdateCount.get());

        capture.close();
    }

    @Test
    @DisplayName("Given execute returns true (query), when getResultSet is called, then a capturing ResultSet is returned")
    void shouldReturnWrappedResultSetWhenExecuteReturnsTrue() throws Exception {
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> latch.countDown()),
                AsyncDispatchConfig.defaults());

        var rawRs = twoRowResultSet();
        var delegate = mock(Statement.class);
        when(delegate.execute("SELECT 1")).thenReturn(true);
        when(delegate.getResultSet()).thenReturn(rawRs);

        var proxy = capturingProxy(delegate, capture);
        var hasRs = proxy.execute("SELECT 1");
        assertTrue(hasRs);

        try (var rs = proxy.getResultSet()) {
            assertFalse(rs == rawRs); // should be a capturing proxy
            while (rs.next()) { /* consume */ }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        capture.close();
    }

    // ── Object methods ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given capturing Statement proxy, when Object methods are called, then they return consistent values")
    void shouldHandleObjectMethodsCorrectly() throws Exception {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        var proxy = capturingProxy(delegate, capture);

        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals(delegate));
        assertEquals(System.identityHashCode(proxy), proxy.hashCode());
        assertTrue(proxy.toString().contains("CapturingStatement"));

        capture.close();
    }

    // ── Delegation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given non-execute methods, when called via capturing proxy, then they are delegated to the underlying Statement")
    void shouldDelegateNonExecuteMethodsToDelegate() throws Exception {
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.getMaxRows()).thenReturn(100);

        var proxy = capturingProxy(delegate, capture);
        assertEquals(100, proxy.getMaxRows());
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
        metadata.setColumnType(1, java.sql.Types.BIGINT);
        metadata.setColumnTypeName(1, "BIGINT");

        var rowSet = javax.sql.rowset.RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow(); rowSet.updateLong(1, 1L); rowSet.insertRow();
        rowSet.moveToInsertRow(); rowSet.updateLong(1, 2L); rowSet.insertRow();
        rowSet.moveToCurrentRow(); rowSet.beforeFirst();
        return rowSet;
    }
}
