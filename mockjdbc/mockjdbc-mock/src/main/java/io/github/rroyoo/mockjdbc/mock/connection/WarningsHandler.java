package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.concurrent.atomic.AtomicReference;

public final class WarningsHandler {

    private final AtomicReference<SQLWarning> warnings = new AtomicReference<>(null);

    public SQLWarning getWarnings() throws SQLException { return warnings.get(); }

    public void clearWarnings() throws SQLException { warnings.set(null); }

    public void addWarning(SQLWarning warning) {
        warnings.accumulateAndGet(warning, (existing, next) -> {
            if (existing == null) return next;
            existing.setNextWarning(next);
            return existing;
        });
    }
}

