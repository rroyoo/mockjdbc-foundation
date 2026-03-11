package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;

import java.sql.Connection;

import static net.bytebuddy.matcher.ElementMatchers.named;

final class ConnectionFactory {

    private ConnectionFactory() {}

    public static Connection create(MockConfig mockConfig) {

        if(mockConfig == null) {
            throw new IllegalArgumentException("MockConfig cannot be null");
        }

        var connectionBuilder = new ByteBuddy()
                .subclass(Connection.class)
                .method(named("commit"))
                .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                .method(named("rollback"))
                .intercept(MethodDelegation.to(GenericVoidMethodHandler.class));

        try(var unloaded = connectionBuilder.make()) {
            return unloaded.load(ConnectionFactory.class.getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
