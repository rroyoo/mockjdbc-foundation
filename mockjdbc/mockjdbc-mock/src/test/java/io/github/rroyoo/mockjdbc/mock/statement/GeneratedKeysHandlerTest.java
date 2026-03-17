package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedKeysHandlerTest {

    @Test
    @DisplayName("Given no stored generated keys, when getGeneratedKeys is called, then it returns an empty ResultSet")
    void shouldReturnEmptyResultSetWhenNoGeneratedKeysAreStored() throws Exception {
        var handler = new GeneratedKeysHandler();

        try (var generatedKeys = handler.getGeneratedKeys()) {
            assertNotNull(generatedKeys);
            assertFalse(generatedKeys.next());
        }
    }

    @Test
    @DisplayName("Given stored generated keys, when getGeneratedKeys is called, then it returns the stored rows")
    void shouldReturnStoredGeneratedKeys() throws Exception {
        var handler = new GeneratedKeysHandler();

        handler.storeGeneratedKeys(singleRowResultSet(5L));

        try (var generatedKeys = handler.getGeneratedKeys()) {
            assertTrue(generatedKeys.next());
            assertEquals(5L, generatedKeys.getLong(1));
            assertFalse(generatedKeys.next());
        }
    }

    @Test
    @DisplayName("Given existing generated keys, when clearGeneratedKeys is called, then current keys are closed and reset")
    void shouldClearAndCloseGeneratedKeys() throws Exception {
        var handler = new GeneratedKeysHandler();
        var closed = new AtomicBoolean(false);
        setGeneratedKeysReference(handler, trackingResultSet(closed));

        handler.clearGeneratedKeys();

        assertTrue(closed.get());
        try (var generatedKeys = handler.getGeneratedKeys()) {
            assertFalse(generatedKeys.next());
        }
    }

    @Test
    @DisplayName("Given an existing generated keys ResultSet, when storing new generated keys, then previous keys are closed")
    void shouldClosePreviousGeneratedKeysWhenStoringNewOnes() throws Exception {
        var handler = new GeneratedKeysHandler();
        var closed = new AtomicBoolean(false);
        setGeneratedKeysReference(handler, trackingResultSet(closed));

        handler.storeGeneratedKeys(singleRowResultSet(8L));

        assertTrue(closed.get());
        try (var generatedKeys = handler.getGeneratedKeys()) {
            assertTrue(generatedKeys.next());
            assertEquals(8L, generatedKeys.getLong(1));
        }
    }

    private static SerializedResultSet singleRowResultSet(long id) {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("id")
                        .setLabel("id")
                        .setSqlType(Types.BIGINT)
                        .setTypeName("BIGINT")
                        .build())
                .addRows(Row.newBuilder()
                        .addValues(JdbcValue.newBuilder().setLongVal(id).build())
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static void setGeneratedKeysReference(GeneratedKeysHandler handler, ResultSet resultSet) throws Exception {
        Field field = GeneratedKeysHandler.class.getDeclaredField("generatedKeys");
        field.setAccessible(true);
        AtomicReference<ResultSet> reference = (AtomicReference<ResultSet>) field.get(handler);
        reference.set(resultSet);
    }

    private static ResultSet trackingResultSet(AtomicBoolean closed) {
        return (ResultSet) Proxy.newProxyInstance(
                GeneratedKeysHandlerTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        closed.set(true);
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return closed.get();
                    }
                    throw new UnsupportedOperationException("Method not implemented in test proxy: " + method.getName());
                }
        );
    }
}

