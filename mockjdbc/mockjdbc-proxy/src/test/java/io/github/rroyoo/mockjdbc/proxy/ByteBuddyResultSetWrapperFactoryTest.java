package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ByteBuddyResultSetWrapperFactoryTest {

    @Test
    @DisplayName("Given JDBC Blob and Clob values, when rows are captured, then their contents are serialized")
    void shouldSerializeBlobAndClobContents() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnType(1)).thenReturn(Types.BLOB);
        when(metadata.getColumnType(2)).thenReturn(Types.CLOB);
        var delegate = mock(ResultSet.class);
        when(delegate.getMetaData()).thenReturn(metadata);
        when(delegate.next()).thenReturn(true, false);
        when(delegate.getObject(1)).thenReturn(new SerialBlob(new byte[]{0, (byte) 0xff, 0x10}));
        when(delegate.getObject(2)).thenReturn(new SerialClob("CLOB payload: café".toCharArray()));
        var captured = new ArrayList<SerializedResultSet>();

        try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, captured::add)) {
            assertTrue(resultSet.next());
            assertFalse(resultSet.next());
        }

        assertEquals(1, captured.size());
        var row = captured.get(0).getRows(0);
        assertEquals(com.google.protobuf.ByteString.copyFrom(new byte[]{0, (byte) 0xff, 0x10}),
                row.getValues(0).getBytesVal());
        assertEquals("CLOB payload: café", row.getValues(1).getStringVal());
    }

    @Test
    @DisplayName("Given null and empty text metadata, when rows are read, then protobuf preserves presence and JDBC values")
    void shouldSerializeNullableMetadataWhileContinuingReading() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metadata.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(metadata.getColumnName(2)).thenReturn("");
        when(metadata.getColumnLabel(2)).thenReturn("label");
        when(metadata.getColumnTypeName(2)).thenReturn("");
        var delegate = mock(ResultSet.class);
        when(delegate.getMetaData()).thenReturn(metadata);
        when(delegate.next()).thenReturn(true, true, false);
        when(delegate.getObject(1)).thenReturn("first", "first", "second", "second");
        when(delegate.getObject(2)).thenReturn("one", "two");
        var captured = new ArrayList<SerializedResultSet>();

        try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, captured::add)) {
            assertTrue(resultSet.next());
            assertEquals("first", resultSet.getObject(1));
            assertTrue(resultSet.next());
            assertEquals("second", resultSet.getObject(1));
            assertFalse(resultSet.next());
        }

        assertEquals(1, captured.size());
        var parsed = SerializedResultSet.parseFrom(captured.get(0).toByteArray());
        var nullable = parsed.getMetadata(0);
        assertFalse(nullable.hasName());
        assertFalse(nullable.hasLabel());
        assertFalse(nullable.hasTypeName());
        var empty = parsed.getMetadata(1);
        assertTrue(empty.hasName());
        assertEquals("", empty.getName());
        assertTrue(empty.hasLabel());
        assertEquals("label", empty.getLabel());
        assertTrue(empty.hasTypeName());
        assertEquals("", empty.getTypeName());
        assertEquals(2, parsed.getRowsCount());
        assertEquals("first", parsed.getRows(0).getValues(0).getStringVal());
        assertEquals("second", parsed.getRows(1).getValues(0).getStringVal());
    }

    @Test
    @DisplayName("Given a metadata capture failure, when rows are read, then failure is logged and JDBC iteration continues")
    void shouldLogCaptureFailureAndContinueReading() throws Exception {
        var delegate = mock(ResultSet.class);
        var failure = new SQLException("metadata failure");
        when(delegate.getMetaData()).thenThrow(failure);
        when(delegate.next()).thenReturn(true, true, false);
        when(delegate.getString(1)).thenReturn("first", "second");
        var captured = new ArrayList<SerializedResultSet>();
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(ByteBuddyResultSetWrapperFactory.class.getName());
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, captured::add)) {
                assertTrue(resultSet.next());
                assertEquals("first", resultSet.getString(1));
                assertTrue(resultSet.next());
                assertEquals("second", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(captured.isEmpty());
        assertEquals(1, records.size());
        assertSame(failure, records.get(0).getThrown());
        verify(delegate).close();
    }

    @Test
    @DisplayName("Given an empty result set and a metadata failure, when it is exhausted, then JDBC returns normally")
    void shouldContinueAfterMetadataFailureOnEmptyResultSet() throws Exception {
        var delegate = mock(ResultSet.class);
        when(delegate.next()).thenReturn(false);
        when(delegate.getMetaData()).thenThrow(new SQLException("metadata failure"));
        var captured = new ArrayList<SerializedResultSet>();

        try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, captured::add)) {
            assertFalse(resultSet.next());
        }
        assertTrue(captured.isEmpty());
        verify(delegate).getMetaData();
        verify(delegate).close();
    }

    @Test
    @DisplayName("Given a row capture failure, when more rows are read, then capture stops without interrupting JDBC")
    void shouldStopCapturingAfterRowFailure() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(Types.VARCHAR);
        var delegate = mock(ResultSet.class);
        when(delegate.getMetaData()).thenReturn(metadata);
        when(delegate.getObject(1)).thenThrow(new SQLException("capture failure"));
        when(delegate.getString(1)).thenReturn("first", "second");
        when(delegate.next()).thenReturn(true, true, false);
        var captured = new ArrayList<SerializedResultSet>();

        try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, captured::add)) {
            assertTrue(resultSet.next());
            assertEquals("first", resultSet.getString(1));
            assertTrue(resultSet.next());
            assertEquals("second", resultSet.getString(1));
            assertFalse(resultSet.next());
        }
        assertTrue(captured.isEmpty());
        verify(delegate).getObject(1);
    }

    @Test
    @DisplayName("Given a capture callback failure, when a batch is published, then JDBC iteration continues")
    void shouldContinueReadingAfterCallbackFailure() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(Types.VARCHAR);
        var delegate = mock(ResultSet.class);
        when(delegate.getMetaData()).thenReturn(metadata);
        when(delegate.getObject(1)).thenReturn("value");
        when(delegate.next()).thenReturn(true, false);

        try (var resultSet = new ByteBuddyResultSetWrapperFactory(1).wrap(delegate, ignored -> {
            throw new IllegalStateException("capture callback failure");
        })) {
            assertTrue(resultSet.next());
            assertEquals("value", resultSet.getObject(1));
            assertFalse(resultSet.next());
        }
    }

    @Test
    @DisplayName("Given a JDBC failure, when reading ResultSet, then the original exception propagates")
    void shouldPropagateDelegateFailure() throws Exception {
        var delegate = mock(ResultSet.class);
        var failure = new SQLException("JDBC failure");
        when(delegate.next()).thenThrow(failure);
        try (var resultSet = new ByteBuddyResultSetWrapperFactory(10).wrap(delegate, ignored -> {})) {
            assertSame(failure, assertThrows(SQLException.class, resultSet::next));
        }
    }
}
