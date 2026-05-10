package io.github.rroyoo.mockjdbc.users.testsupport.tcp;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TcpServiceAvailableCondition.class)
public @interface RequiresTcpService {

    String name() default "TCP service";

    String host() default "localhost";

    int port() default -1;

    String hostEnv() default "";

    String portEnv() default "";

    int timeoutMs() default 250;
}
