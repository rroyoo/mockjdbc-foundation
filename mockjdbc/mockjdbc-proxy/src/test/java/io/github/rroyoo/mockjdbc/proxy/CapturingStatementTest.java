package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static void awaitDispatchStats(JdbcExecutionCapture capture, AsyncDispatchStats expected)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!capture.dispatcher.stats().equals(expected) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(expected, capture.dispatcher.stats());
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
    @DisplayName("Given Kafka delivery fails asynchronously, when executeUpdate is called, then its JDBC result is returned and failure is counted")
    void shouldReturnUpdateCountWhenKafkaDeliveryFails() throws Exception {
        var sendStarted = new CountDownLatch(1);
        var producer = new MockedQueryEventProducer() {
            @Override
            public void send(MockedQuery event) {
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> sendAsync(MockedQuery event) {
                sendStarted.countDown();
                return CompletableFuture.failedFuture(new IllegalStateException("broker down"));
            }
        };
        var capture = new JdbcExecutionCapture("default-datasource", event -> {}, producer,
                new AsyncDispatchConfig(4, 1, AsyncDispatchConfig.OverflowStrategy.DROP_NEWEST, 64));
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("UPDATE users SET active = true")).thenReturn(5);
        var statement = capturingProxy(delegate, capture);

        assertEquals(5, statement.executeUpdate("UPDATE users SET active = true"));
        assertTrue(sendStarted.await(2, TimeUnit.SECONDS));
        awaitDispatchStats(capture, new AsyncDispatchStats(1, 0, 0, 1, 0));
        assertEquals(1, capture.events().size());

        capture.close();
    }

    @Test
    @DisplayName("Given a full capture queue, when executeUpdate is called, then JDBC results are returned and DROP_NEWEST is counted")
    void shouldReturnUpdateCountWhenCaptureQueueOverflows() throws Exception {
        var firstSendStarted = new CountDownLatch(1);
        var releaseFirstSend = new Semaphore(0);
        var sendCount = new AtomicLong();
        var producer = (MockedQueryEventProducer) event -> {
            if (sendCount.incrementAndGet() == 1) {
                firstSendStarted.countDown();
                releaseFirstSend.acquireUninterruptibly();
            }
        };
        var capture = new JdbcExecutionCapture("default-datasource", event -> {}, producer,
                new AsyncDispatchConfig(2, 1, AsyncDispatchConfig.OverflowStrategy.DROP_NEWEST, 64));
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("first")).thenReturn(7);
        when(delegate.executeUpdate("second")).thenReturn(7);
        when(delegate.executeUpdate("third")).thenReturn(7);
        when(delegate.executeUpdate("fourth")).thenReturn(7);
        var statement = capturingProxy(delegate, capture);

        try {
            assertEquals(7, statement.executeUpdate("first"));
            assertTrue(firstSendStarted.await(2, TimeUnit.SECONDS));
            assertEquals(7, statement.executeUpdate("second"));
            assertEquals(7, statement.executeUpdate("third"));
            assertEquals(7, statement.executeUpdate("fourth"));
            assertEquals(4, capture.events().size());
            assertEquals(new AsyncDispatchStats(4, 0, 1, 0, 2), capture.dispatcher.stats());

            releaseFirstSend.release();
            awaitDispatchStats(capture, new AsyncDispatchStats(4, 3, 1, 0, 0));
        } finally {
            releaseFirstSend.release();
            capture.close();
        }
    }

    @Test
    @DisplayName("Given generated keys requested on executeUpdate, when consumed, then the capture event includes them")
    void shouldCaptureGeneratedKeysFromExecuteUpdate() throws Exception {
        var capturedQuery = new java.util.concurrent.atomic.AtomicReference<io.github.rroyoo.mockjdbc.mock.MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("users", event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    capturedQuery.set(query);
                    latch.countDown();
                }),
                AsyncDispatchConfig.defaults());
        var keys = twoRowResultSet();
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS)).thenReturn(1);
        when(delegate.getGeneratedKeys()).thenReturn(keys);

        var statement = capturingProxy(delegate, capture);
        assertEquals(1, statement.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS));
        try (var generatedKeys = statement.getGeneratedKeys()) {
            assertTrue(generatedKeys.next());
            assertEquals(1L, generatedKeys.getLong(1));
            assertTrue(generatedKeys.next());
            assertEquals(2L, generatedKeys.getLong(1));
            assertFalse(generatedKeys.next());
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(capturedQuery.get().hasGeneratedKeys());
        assertEquals(2, capturedQuery.get().getGeneratedKeys().getRowsCount());
        assertEquals(1L, capturedQuery.get().getUpdateCount());
        capture.close();
    }

    @Test
    @DisplayName("Given generated-key retrieval fails, when executeUpdate succeeds, then the update remains successful")
    void shouldNotRetrieveGeneratedKeysBeforeCallerRequestsThem() throws Exception {
        var capture = new JdbcExecutionCapture("users", event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {}),
                AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS)).thenReturn(1);
        when(delegate.getGeneratedKeys()).thenThrow(new java.sql.SQLException("keys unavailable"));

        var statement = capturingProxy(delegate, capture);

        assertEquals(1, statement.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS));
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                java.sql.SQLException.class, statement::getGeneratedKeys);
        assertEquals("keys unavailable", error.getMessage());
        assertEquals(1, capture.events().size());
        assertTrue(capture.events().get(0).success());

        capture.close();
    }

    @Test
    @DisplayName("Given a pending generated-key capture, when a batch executes, then the prior update is captured before batch state changes")
    void shouldFlushPendingGeneratedKeyCaptureBeforeBatchExecution() throws Exception {
        var capture = new JdbcExecutionCapture("users", event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {}),
                AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS)).thenReturn(1);
        when(delegate.executeBatch()).thenReturn(new int[]{1});
        var statement = capturingProxy(delegate, capture);

        statement.executeUpdate("INSERT INTO users(name) VALUES ('Ada')", Statement.RETURN_GENERATED_KEYS);
        statement.addBatch("UPDATE users SET name='Grace'");
        statement.executeBatch();

        assertEquals(2, capture.events().size());
        assertEquals("INSERT INTO users(name) VALUES ('Ada')", capture.events().get(0).sql());
        assertEquals("UPDATE users SET name='Grace'", capture.events().get(1).sql());
        assertTrue(capture.events().get(0).success());
        assertTrue(capture.events().get(1).success());
        verify(delegate).executeBatch();
        capture.close();
    }

    @Test
    @DisplayName("Given a plain statement batch, when executeBatch runs, then each SQL and returned count are captured")
    void shouldCapturePlainStatementBatchEntries() throws Exception {
        var protoEvents = new java.util.concurrent.CopyOnWriteArrayList<io.github.rroyoo.mockjdbc.mock.MockedQuery>();
        var published = new CountDownLatch(2);
        var events = new java.util.concurrent.CopyOnWriteArrayList<JdbcQueryInterceptedEvent>();
        var capture = new JdbcExecutionCapture("users", events::add,
                MockedQueryEventProducer.fromConsumer(query -> {
                    protoEvents.add(query);
                    published.countDown();
                }),
                AsyncDispatchConfig.defaults());
        var firstSql = "UPDATE users SET active=true WHERE id=1";
        var secondSql = "UPDATE users SET active=true WHERE id=2";
        var delegate = mock(Statement.class);
        when(delegate.executeBatch()).thenReturn(new int[]{2, Statement.SUCCESS_NO_INFO});
        var statement = capturingProxy(delegate, capture);

        statement.addBatch(firstSql);
        statement.addBatch(secondSql);
        assertEquals(2, statement.executeBatch().length);

        assertTrue(published.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(firstSql, secondSql), events.stream().map(JdbcQueryInterceptedEvent::sql).toList());
        assertEquals(2, protoEvents.size());
        assertTrue(protoEvents.stream().allMatch(io.github.rroyoo.mockjdbc.mock.MockedQuery::hasBatchIndex));
        var firstEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 0).findFirst().orElseThrow();
        var secondEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 1).findFirst().orElseThrow();
        assertEquals(2L, firstEntry.getUpdateCount());
        assertEquals((long) Statement.SUCCESS_NO_INFO, secondEntry.getUpdateCount());
        assertTrue(protoEvents.stream().allMatch(io.github.rroyoo.mockjdbc.mock.MockedQuery::hasSimpleStatement));
        capture.close();
    }

    @Test
    @DisplayName("Given a partially failed plain statement batch, when execution throws, then completed counts and failure index are captured")
    void shouldCapturePartialPlainStatementBatchFailure() throws Exception {
        var protoEvents = new java.util.concurrent.CopyOnWriteArrayList<io.github.rroyoo.mockjdbc.mock.MockedQuery>();
        var published = new CountDownLatch(2);
        var capture = new JdbcExecutionCapture("users", event -> { },
                MockedQueryEventProducer.fromConsumer(query -> {
                    protoEvents.add(query);
                    published.countDown();
                }),
                AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.executeBatch()).thenThrow(new java.sql.BatchUpdateException("batch failed", new int[]{2}));
        var statement = capturingProxy(delegate, capture);

        statement.addBatch("UPDATE users SET active=true WHERE id=1");
        statement.addBatch("UPDATE users SET active=true WHERE id=2");
        statement.addBatch("UPDATE users SET active=true WHERE id=3");

        assertThrows(java.sql.BatchUpdateException.class, statement::executeBatch);
        assertTrue(published.await(2, TimeUnit.SECONDS));
        assertEquals(2, protoEvents.size());
        assertTrue(protoEvents.stream().allMatch(io.github.rroyoo.mockjdbc.mock.MockedQuery::hasBatchIndex));
        var firstEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 0).findFirst().orElseThrow();
        var secondEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 1).findFirst().orElseThrow();
        assertEquals(2L, firstEntry.getUpdateCount());
        assertEquals(io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS,
                firstEntry.getStatus());
        assertEquals((long) Statement.EXECUTE_FAILED, secondEntry.getUpdateCount());
        assertEquals(io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR,
                secondEntry.getStatus());
        capture.close();
    }

    @Test
    @DisplayName("Given a partially failed plain large batch, when execution throws, then large completed counts are captured")
    void shouldCapturePartialPlainStatementLargeBatchFailure() throws Exception {
        var protoEvents = new java.util.concurrent.CopyOnWriteArrayList<io.github.rroyoo.mockjdbc.mock.MockedQuery>();
        var published = new CountDownLatch(2);
        var capture = new JdbcExecutionCapture("users", event -> { },
                MockedQueryEventProducer.fromConsumer(query -> {
                    protoEvents.add(query);
                    published.countDown();
                }),
                AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.executeLargeBatch()).thenThrow(new java.sql.BatchUpdateException(
                "batch failed", null, 0,
                new long[]{(long) Integer.MAX_VALUE + 5, Statement.EXECUTE_FAILED}, null));
        var statement = capturingProxy(delegate, capture);

        statement.addBatch("UPDATE users SET active=true WHERE id=1");
        statement.addBatch("UPDATE users SET active=true WHERE id=2");
        statement.addBatch("UPDATE users SET active=true WHERE id=3");

        assertThrows(java.sql.BatchUpdateException.class, statement::executeLargeBatch);
        assertTrue(published.await(2, TimeUnit.SECONDS));
        assertEquals(2, protoEvents.size());
        assertTrue(protoEvents.stream().allMatch(io.github.rroyoo.mockjdbc.mock.MockedQuery::hasBatchIndex));
        var firstEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 0).findFirst().orElseThrow();
        var secondEntry = protoEvents.stream().filter(query -> query.getBatchIndex() == 1).findFirst().orElseThrow();
        assertEquals((long) Integer.MAX_VALUE + 5, firstEntry.getUpdateCount());
        assertEquals((long) Statement.EXECUTE_FAILED, secondEntry.getUpdateCount());
        capture.close();
    }

    @Test
    @DisplayName("Given partially consumed generated keys, when the statement closes, then the update event is finalized")
    void shouldFinalizeGeneratedKeyCaptureWhenStatementCloses() throws Exception {
        var capturedQuery = new java.util.concurrent.atomic.AtomicReference<io.github.rroyoo.mockjdbc.mock.MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("users", event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    capturedQuery.set(query);
                    latch.countDown();
                }),
                AsyncDispatchConfig.defaults());
        var delegate = mock(Statement.class);
        when(delegate.executeUpdate("INSERT INTO users(name) VALUES ('Ada')",
                Statement.RETURN_GENERATED_KEYS)).thenReturn(1);
        when(delegate.getGeneratedKeys()).thenReturn(twoRowResultSet());
        var statement = capturingProxy(delegate, capture);

        statement.executeUpdate("INSERT INTO users(name) VALUES ('Ada')", Statement.RETURN_GENERATED_KEYS);
        var keys = statement.getGeneratedKeys();
        assertTrue(keys.next());
        statement.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(capturedQuery.get().hasGeneratedKeys());
        assertEquals(1, capturedQuery.get().getGeneratedKeys().getRowsCount());
        assertEquals(1, capture.events().size());
        capture.close();
    }

    @Test
    @DisplayName("Given a generated-keys statement overload, when its argument count is checked, then holdability is not mistaken for a key flag")
    void shouldOnlyRecognizeGeneratedKeysOnTwoArgumentOverloads() {
        assertTrue(CapturingStatementInvocationHandler.requestsGeneratedKeys(
                new Object[]{"UPDATE users SET active=true", Statement.RETURN_GENERATED_KEYS}));
        assertFalse(CapturingStatementInvocationHandler.requestsGeneratedKeys(
                new Object[]{"SELECT 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
                        ResultSet.HOLD_CURSORS_OVER_COMMIT}));
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
