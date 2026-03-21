package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceIdentityResolverTest {

    @Test
    @DisplayName("Given explicit alias, when resolving datasource id, then explicit alias has precedence")
    void shouldPreferExplicitAlias() {
        var dataSource = mock(DataSource.class);

        var resolved = DatasourceIdentityResolver.resolve(" users-primary ", "usersBean", dataSource);

        assertEquals("users-primary", resolved);
    }

    @Test
    @DisplayName("Given no explicit alias, when bean name exists, then bean name is used")
    void shouldUseBeanNameWhenAliasIsMissing() {
        var dataSource = mock(DataSource.class);

        var resolved = DatasourceIdentityResolver.resolve(null, "ordersBean", dataSource);

        assertEquals("ordersBean", resolved);
    }

    @Test
    @DisplayName("Given neither alias nor bean name, when datasource metadata exists, then fingerprint is stable")
    void shouldUseStableMetadataFingerprintFallback() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(java.sql.Connection.class);
        var metaData = mock(java.sql.DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:postgresql://db:5432/app");
        when(metaData.getUserName()).thenReturn("app_user");
        when(metaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver");

        var first = DatasourceIdentityResolver.resolve(null, null, dataSource);
        var second = DatasourceIdentityResolver.resolve("", "   ", dataSource);

        assertEquals(first, second);
        assertTrue(first.startsWith("ds-"));
        assertEquals(19, first.length());
    }
}
