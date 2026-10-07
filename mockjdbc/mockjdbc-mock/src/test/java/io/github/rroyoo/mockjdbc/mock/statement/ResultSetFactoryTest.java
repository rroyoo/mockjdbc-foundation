package io.github.rroyoo.mockjdbc.mock.statement;

import com.google.protobuf.Timestamp;
import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	@ParameterizedTest(name = "[{index}] {1} -> {3}")
	@MethodSource("typedValues")
	@DisplayName("Given a declared SQL type, when creating ResultSet, then getObject returns the matching Java type")
	void shouldCoerceValuesToDeclaredSqlType(int sqlType, JdbcValue value, Class<?> expectedClass, Object expected) throws Exception {
		var serialized = singleValue(sqlType, value);

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertTrue(resultSet.next());
			var actual = resultSet.getObject(1);
			assertInstanceOf(expectedClass, actual);
			assertEquals(expected, actual);
			assertFalse(resultSet.wasNull());
		}
	}

	static Stream<Arguments> typedValues() {
		var instant = Instant.parse("2026-03-17T10:15:30Z");
		var zoned = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
		var protoTimestamp = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
		return Stream.of(
				Arguments.of(Types.TINYINT, longValue(7), Integer.class, 7),
				Arguments.of(Types.SMALLINT, longValue(300), Integer.class, 300),
				Arguments.of(Types.INTEGER, longValue(42), Integer.class, 42),
				Arguments.of(Types.BIGINT, longValue(42), Long.class, 42L),
				Arguments.of(Types.REAL, JdbcValue.newBuilder().setDoubleVal(1.5d).build(), Float.class, 1.5f),
				Arguments.of(Types.DOUBLE, JdbcValue.newBuilder().setDoubleVal(2.5d).build(), Double.class, 2.5d),
				Arguments.of(Types.DECIMAL, longValue(10), BigDecimal.class, BigDecimal.valueOf(10)),
				Arguments.of(Types.NUMERIC, JdbcValue.newBuilder().setDecimalVal("12.340").build(), BigDecimal.class, new BigDecimal("12.340")),
				Arguments.of(Types.BOOLEAN, JdbcValue.newBuilder().setBoolVal(true).build(), Boolean.class, true),
				Arguments.of(Types.BIT, longValue(0), Boolean.class, false),
				Arguments.of(Types.DATE, JdbcValue.newBuilder().setStringVal("2026-03-17").build(), Date.class, Date.valueOf("2026-03-17")),
				Arguments.of(Types.DATE, JdbcValue.newBuilder().setStringVal("2026-03-17 00:00:00.0").build(), Date.class, Date.valueOf("2026-03-17")),
				Arguments.of(Types.DATE, JdbcValue.newBuilder().setTimestampVal(protoTimestamp).build(), Date.class, Date.valueOf(zoned.toLocalDate())),
				Arguments.of(Types.TIME, JdbcValue.newBuilder().setStringVal("10:15:30").build(), Time.class, Time.valueOf("10:15:30")),
				Arguments.of(Types.TIME, JdbcValue.newBuilder().setStringVal("1970-01-01 10:15:30.0").build(), Time.class, Time.valueOf("10:15:30")),
				Arguments.of(Types.TIMESTAMP, JdbcValue.newBuilder().setStringVal("2026-03-17 10:15:30.5").build(), java.sql.Timestamp.class, java.sql.Timestamp.valueOf("2026-03-17 10:15:30.5")),
				Arguments.of(Types.TIMESTAMP, JdbcValue.newBuilder().setTimestampVal(protoTimestamp).build(), java.sql.Timestamp.class, java.sql.Timestamp.from(instant)),
				Arguments.of(Types.VARCHAR, JdbcValue.newBuilder().setStringVal("text").build(), String.class, "text")
		);
	}

	@ParameterizedTest(name = "[{index}] sqlType {0}")
	@MethodSource("sqlTypes")
	@DisplayName("Given a null value, when creating ResultSet, then getObject returns null and wasNull is true")
	void shouldReturnNullAndFlagWasNullForEverySqlType(int sqlType) throws Exception {
		var serialized = singleValue(sqlType, JdbcValue.newBuilder().setIsNull(true).build());

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertTrue(resultSet.next());
			assertNull(resultSet.getObject(1));
			assertTrue(resultSet.wasNull());
		}
	}

	static Stream<Integer> sqlTypes() {
		return Stream.of(Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.REAL, Types.DOUBLE,
				Types.DECIMAL, Types.BOOLEAN, Types.DATE, Types.TIME, Types.TIMESTAMP, Types.VARCHAR, Types.BINARY);
	}

	@Test
	@DisplayName("Given a null primitive column, when reading with getInt, then zero is returned and wasNull is true")
	void shouldReturnZeroForNullPrimitiveAccessors() throws Exception {
		var serialized = singleValue(Types.INTEGER, JdbcValue.newBuilder().setIsNull(true).build());

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertTrue(resultSet.next());
			assertEquals(0, resultSet.getInt(1));
			assertTrue(resultSet.wasNull());
		}
	}

	@Test
	@DisplayName("Given a value out of range for the declared type, when creating ResultSet, then SQLDataException is thrown")
	void shouldFailWhenValueOverflowsDeclaredType() {
		var serialized = singleValue(Types.TINYINT, longValue(300));

		assertThrows(SQLDataException.class, () -> ResultSetFactory.create(serialized));
	}

	@ParameterizedTest
	@ValueSource(ints = {Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE})
	@DisplayName("Given a time zone aware column, when creating ResultSet, then SQLFeatureNotSupportedException is thrown")
	void shouldRejectTimeZoneAwareColumns(int sqlType) {
		var serialized = singleValue(sqlType, JdbcValue.newBuilder().setStringVal("2024-01-01T00:00:00+01:00").build());

		assertThrows(java.sql.SQLFeatureNotSupportedException.class, () -> ResultSetFactory.create(serialized));
	}

	@Test
	@DisplayName("Given an unparsable value for the declared type, when creating ResultSet, then SQLDataException is thrown")
	void shouldFailWhenValueCannotBeConvertedToDeclaredType() {
		var serialized = singleValue(Types.DATE, JdbcValue.newBuilder().setStringVal("not-a-date").build());

		assertThrows(SQLDataException.class, () -> ResultSetFactory.create(serialized));
	}

	@Test
	@DisplayName("Given a row shorter than the metadata, when creating ResultSet, then missing columns are null")
	void shouldPadShortRowsWithNulls() throws Exception {
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("a", "a", Types.VARCHAR, "VARCHAR"))
				.addMetadata(column("b", "b", Types.VARCHAR, "VARCHAR"))
				.addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setStringVal("x").build()).build())
				.build();

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertTrue(resultSet.next());
			assertEquals("x", resultSet.getString(1));
			assertNull(resultSet.getString(2));
			assertTrue(resultSet.wasNull());
		}
	}

	@Test
	@DisplayName("Given a row longer than the metadata, when creating ResultSet, then SQLException is thrown")
	void shouldRejectRowsWithMoreValuesThanColumns() {
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("a", "a", Types.VARCHAR, "VARCHAR"))
				.addRows(Row.newBuilder()
						.addValues(JdbcValue.newBuilder().setStringVal("x").build())
						.addValues(JdbcValue.newBuilder().setStringVal("y").build())
						.build())
				.build();

		assertThrows(SQLException.class, () -> ResultSetFactory.create(serialized));
	}

	@Test
	@DisplayName("Given metadata without rows, when creating ResultSet, then it is empty but keeps column metadata")
	void shouldExposeMetadataForEmptyResultSet() throws Exception {
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("id", "ID", Types.BIGINT, "BIGINT"))
				.addMetadata(column("amount", "AMOUNT", Types.DECIMAL, "DECIMAL"))
				.build();

		try (var resultSet = ResultSetFactory.create(serialized)) {
			assertFalse(resultSet.next());
			var metaData = resultSet.getMetaData();
			assertEquals(2, metaData.getColumnCount());
			assertEquals("ID", metaData.getColumnLabel(1));
			assertEquals("id", metaData.getColumnName(1));
			assertEquals(Types.DECIMAL, metaData.getColumnType(2));
			assertEquals("DECIMAL", metaData.getColumnTypeName(2));
		}
	}

	@Test
	@DisplayName("Given typed columns, when reading metadata, then signedness follows the declared type and nullability is unknown")
	void shouldDeriveSignednessAndNullabilityFromMetadata() throws Exception {
		var serialized = SerializedResultSet.newBuilder()
				.addMetadata(column("n", "n", Types.INTEGER, "INTEGER"))
				.addMetadata(column("s", "s", Types.VARCHAR, "VARCHAR"))
				.build();

		try (var resultSet = ResultSetFactory.create(serialized)) {
			var metaData = resultSet.getMetaData();
			assertTrue(metaData.isSigned(1));
			assertFalse(metaData.isSigned(2));
			assertEquals(ResultSetMetaData.columnNullableUnknown, metaData.isNullable(1));
		}
	}

	private static SerializedResultSet singleValue(int sqlType, JdbcValue value) {
		return SerializedResultSet.newBuilder()
				.addMetadata(column("c", "c", sqlType, "T"))
				.addRows(Row.newBuilder().addValues(value).build())
				.build();
	}

	private static JdbcValue longValue(long number) {
		return JdbcValue.newBuilder().setLongVal(number).build();
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

