package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DefaultDataSourceProxyFactoryTest {

    private final DefaultDataSourceProxyFactory factory = new DefaultDataSourceProxyFactory();

    @Test
    void shouldCreateProxyForProvidedDataSource() {
        DataSource originalDataSource = mock(DataSource.class);

        DataSource proxiedDataSource = factory.create(originalDataSource);

        assertNotNull(proxiedDataSource);
    }

    @Test
    void shouldRejectNullDataSource() {
        assertThrows(NullPointerException.class, () -> factory.create(null));
    }
}

