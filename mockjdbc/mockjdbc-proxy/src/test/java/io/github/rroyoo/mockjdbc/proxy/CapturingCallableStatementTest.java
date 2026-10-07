package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.Types;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers CallableStatement OUT/INOUT parameter capture via {@code registerOutParameter} +
 * post-execution {@code getXxx(int)} retrieval, deferred until the statement's lifecycle
 * flushes the pending capture (next execution or {@code close()}).
 */
class CapturingCallableStatementTest {

    private static CallableStatement capturingProxy(CallableStatement delegate,
                                                     String sql,
                                                     JdbcExecutionCapture capture) {
        return (CallableStatement) java.lang.reflect.Proxy.newProxyInstance(
                CallableStatement.class.getClassLoader(),
                new Class<?>[]{ CallableStatement.class },
                new CapturingPreparedStatementInvocationHandler(delegate, sql, capture)
        );
    }

    @Test
    @DisplayName("Given a pure OUT parameter registered and retrieved after executeUpdate, when statement is closed, then the deferred event carries the OUT value and mode")
    void shouldCapturePureOutParameterOnClose() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(CallableStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);
        when(delegate.getInt(1)).thenReturn(42);

        var proxy = capturingProxy(delegate, "{ call compute_total(?) }", capture);
        proxy.registerOutParameter(1, Types.INTEGER);
        proxy.executeUpdate();
        assertEquals(42, proxy.getInt(1));

        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(protoEvent.get());
        assertTrue(protoEvent.get().hasCallableStatement());
        var params = protoEvent.get().getCallableStatement().getParametersList();
        assertEquals(1, params.size());
        assertEquals(ParameterMetaData.parameterModeOut, params.get(0).getMode());
        assertEquals(42L, params.get(0).getValue().getLongVal());

        capture.close();
    }

    @Test
    @DisplayName("Given an INOUT parameter bound then re-read after execution, when flushed, then the retrieved OUT value overrides the bound IN value")
    void shouldCaptureInOutParameterWithRetrievedValueOverridingBoundValue() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(CallableStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);
        when(delegate.getInt(1)).thenReturn(100);

        var proxy = capturingProxy(delegate, "{ call apply_discount(?) }", capture);
        proxy.setInt(1, 10);
        proxy.registerOutParameter(1, Types.INTEGER);
        proxy.executeUpdate();
        assertEquals(100, proxy.getInt(1));

        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        var params = protoEvent.get().getCallableStatement().getParametersList();
        assertEquals(1, params.size());
        assertEquals(ParameterMetaData.parameterModeInOut, params.get(0).getMode());
        assertEquals(100L, params.get(0).getValue().getLongVal());

        capture.close();
    }

    @Test
    @DisplayName("Given an OUT parameter never retrieved by the caller, when flushed, then the parameter value falls back to null")
    void shouldFallBackToNullWhenOutValueNeverRetrieved() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(CallableStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);

        var proxy = capturingProxy(delegate, "{ call noop_proc(?) }", capture);
        proxy.registerOutParameter(1, Types.INTEGER);
        proxy.executeUpdate();
        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        var params = protoEvent.get().getCallableStatement().getParametersList();
        assertEquals(1, params.size());
        assertEquals(ParameterMetaData.parameterModeOut, params.get(0).getMode());
        assertTrue(params.get(0).getValue().getIsNull());

        capture.close();
    }

    @Test
    @DisplayName("Given two sequential executions with OUT parameters, when the second executes, then the first call's deferred event is flushed automatically")
    void shouldFlushPreviousDeferredCaptureOnNextExecution() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(2);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { events.add(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(CallableStatement.class);
        when(delegate.executeUpdate()).thenReturn(0);
        when(delegate.getInt(1)).thenReturn(1).thenReturn(2);

        var proxy = capturingProxy(delegate, "{ call gen_id(?) }", capture);

        proxy.registerOutParameter(1, Types.INTEGER);
        proxy.executeUpdate();
        assertEquals(1, proxy.getInt(1));

        // Second execution flushes the first deferred capture before starting its own cycle.
        proxy.executeUpdate();
        assertEquals(2, proxy.getInt(1));
        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).getCallableStatement().getParameters(0).getValue().getLongVal());
        assertEquals(2L, events.get(1).getCallableStatement().getParameters(0).getValue().getLongVal());

        capture.close();
    }

    @Test
    @DisplayName("Given executeUpdate throws with an OUT parameter registered, when flushed on close, then the deferred event captures the error")
    void shouldCaptureErrorForDeferredCallableExecution() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);
        var capture = new JdbcExecutionCapture("default-datasource", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());

        var delegate = mock(CallableStatement.class);
        when(delegate.executeUpdate()).thenThrow(new java.sql.SQLException("proc failed"));

        var proxy = capturingProxy(delegate, "{ call failing_proc(?) }", capture);
        proxy.registerOutParameter(1, Types.INTEGER);

        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, proxy::executeUpdate);
        proxy.close();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(protoEvent.get().getStatus() == io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS);
        assertEquals("proc failed", protoEvent.get().getError().getMessage());

        capture.close();
    }
}
