package io.github.rroyoo.mockjdbc.mock.statement;

import com.google.protobuf.Timestamp;
import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultSetFactoryTest {

	@Test
	@DisplayName("Given a serialized result set, when creating ResultSet, then values are converted to JDBC-friendly Java types")
	void shouldConvertSerializedValuesToJdbcTypes() throws Exception {
		var instant = Instant.parse("2026-03-17T10:15:30Z");
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("text_col", "text_col", Types.VARCHAR, "VARCHAR"))
				.addMetadata(column("long_col", "long_col", Types.BIGINT, "BIGINT"))
				.addMetadata(column("double_col", "double_col", Types.DOUBLE, "DOUBLE"))
				.addMetadata(column("bool_col", "bool_col", Types.BOOLEAN, "BOOLEAN"))
				.addMetadata(column("bytes_col", "bytes_col", Types.BINARY, "BINARY"))
				.addMetadata(column("decimal_col", "decimal_col", Types.DECIMAL, "DECIMAL"))
				.addMetadata(column("ts_col", "ts_col", Types.TIMESTAMP, "TIMESTAMP"))
				.addMetadata(column("null_col", "null_col", Types.VARCHAR, "VARCHAR"))
				.addRows(Row.newBuilder()
						.addValues(JdbcValue.newBuilder().setStringVal("hello").build())
						.addValues(JdbcValue.newBuilder().setLongVal(42L).build())
						.addValues(JdbcValue.newBuilder().setDoubleVal(3.14d).build())
						.addValues(JdbcValue.newBuilder().setBoolVal(true).build())
						.addValues(JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3})).build())
						.addValues(JdbcValue.newBuilder().setDecimalVal("123.45").build())
						.addValues(JdbcValue.newBuilder().setTimestampVal(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build())
						.addValues(JdbcValue.newBuilder().setIsNull(true).build())
						.build())
				.build();

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertTrue(resultSet.next());
			assertEquals("hello", resultSet.getString(1));
			assertEquals(42L, resultSet.getLong(2));
			assertEquals(3.14d, resultSet.getDouble(3), 0.0001d);
			assertTrue(resultSet.getBoolean(4));
			assertArrayEquals(new byte[]{1, 2, 3}, resultSet.getBytes(5));
			assertEquals(new BigDecimal("123.45"), resultSet.getBigDecimal(6));
			assertEquals(java.sql.Timestamp.from(instant), resultSet.getTimestamp(7));
			assertNull(resultSet.getObject(8));
		}
	}

	@Test
	@DisplayName("Given blank label or name in metadata, when creating ResultSet, then metadata falls back to the non-blank value")
	void shouldApplyColumnNameAndLabelFallbacks() throws Exception {
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("name_from_column", "", Types.VARCHAR, "VARCHAR"))
				.addMetadata(column("", "label_from_column", Types.VARCHAR, "VARCHAR"))
				.addRows(Row.newBuilder()
						.addValues(JdbcValue.newBuilder().setStringVal("a").build())
						.addValues(JdbcValue.newBuilder().setStringVal("b").build())
						.build())
				.build();

		try (var resultSet = ResultSetFactory.create(serialized)) {
			var metaData = resultSet.getMetaData();
			assertEquals("name_from_column", metaData.getColumnLabel(1));
			assertEquals("name_from_column", metaData.getColumnName(1));
			assertEquals("label_from_column", metaData.getColumnLabel(2));
			assertEquals("label_from_column", metaData.getColumnName(2));
		}
	}

	private static ColumnMetadata column(String name, String label, int sqlType, String typeName) {
		return ColumnMetadata.newBuilder()
				.setName(name)
				.setLabel(label)
				.setSqlType(sqlType)
				.setTypeName(typeName)
				.build();
	}
}

