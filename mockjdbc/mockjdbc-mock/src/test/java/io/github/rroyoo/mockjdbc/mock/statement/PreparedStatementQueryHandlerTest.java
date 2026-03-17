package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreparedStatementQueryHandlerTest {

	@Test
	@DisplayName("Given parameters set out of order, when getParameterMetaData is called, then metadata follows parameter index order")
	void shouldExposeParameterMetadataInIndexOrder() throws Exception {
		var handler = new PreparedStatementQueryHandler(
				mockConfig(),
				new StatementLifecycleHandler(),
				new StatementExecutionStateHandler(),
				new GeneratedKeysHandler(),
				false,
				"SELECT ?, ?"
		);

		handler.setInt(2, 99);
		handler.setString(1, "alice");

		var metaData = handler.getParameterMetaData();

		assertEquals(2, metaData.getParameterCount());
		assertEquals(Types.VARCHAR, metaData.getParameterType(1));
		assertEquals(Types.INTEGER, metaData.getParameterType(2));
	}

	@Test
	@DisplayName("Given parameters already set, when clearParameters is called, then parameter metadata count becomes zero")
	void shouldClearAllParameters() throws Exception {
		var handler = new PreparedStatementQueryHandler(
				mockConfig(),
				new StatementLifecycleHandler(),
				new StatementExecutionStateHandler(),
				new GeneratedKeysHandler(),
				false,
				"SELECT ?"
		);

		handler.setString(1, "bob");
		handler.clearParameters();

		assertEquals(0, handler.getParameterMetaData().getParameterCount());
	}

	@Test
	@DisplayName("Given a prepared statement handler, when getMetaData is called before execution, then it returns null")
	void shouldReturnNullResultSetMetadataBeforeExecution() throws Exception {
		var handler = new PreparedStatementQueryHandler(
				mockConfig(),
				new StatementLifecycleHandler(),
				new StatementExecutionStateHandler(),
				new GeneratedKeysHandler(),
				false,
				"SELECT ?"
		);

		assertNull(handler.getMetaData());
	}

	private static MockConfig mockConfig() {
		return new MockConfig(new MockConfig.MockServer("127.0.0.1", 50051), new Properties());
	}
}

