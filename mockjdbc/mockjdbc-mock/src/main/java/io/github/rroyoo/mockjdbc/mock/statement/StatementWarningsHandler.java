package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.concurrent.atomic.AtomicReference;

final class StatementWarningsHandler {

    private final AtomicReference<SQLWarning> warnings = new AtomicReference<>(null);

    public SQLWarning getWarnings() throws SQLException {
        return warnings.get();
    }

    public void clearWarnings() throws SQLException {
        warnings.set(null);
    }
}

