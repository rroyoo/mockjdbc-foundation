package io.github.rroyoo.mockjdbc.proxy;

import net.ttddyy.dsproxy.ConnectionInfo;
import net.ttddyy.dsproxy.proxy.ProxyConfig;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogic;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogicFactory;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static io.github.rroyoo.mockjdbc.proxy.JdbcQueryCaptureListener.PENDING_QUERY;

/**
 * datasource-proxy {@link ResultSetProxyLogicFactory} that intercepts ResultSet iteration for SELECT queries.
 *
 * <p>Works in tandem with {@link JdbcQueryCaptureListener}: the listener stores the current query SQL
 * and parameters in a {@link ThreadLocal} during {@code beforeQuery()}, and this factory reads that
 * context on {@link #create} to build a capturing proxy logic that emits a {@link io.github.rroyoo.mockjdbc.mock.MockedQuery}
 * proto event when the ResultSet is fully consumed or closed.
 *
 * <p>If no pending query context is available (e.g. the ResultSet was not produced by a tracked query),
 * this factory falls back to the datasource-proxy default logic.
 */
final class CapturingResultSetProxyLogicFactory implements ResultSetProxyLogicFactory {

    private final String datasourceId;
    private final AsyncMockedQueryEventDispatcher dispatcher;
    private final ByteBuddyResultSetWrapperFactory wrapperFactory;

    CapturingResultSetProxyLogicFactory(String datasourceId,
                                        AsyncMockedQueryEventDispatcher dispatcher,
                                        ByteBuddyResultSetWrapperFactory wrapperFactory) {
        this.datasourceId = datasourceId;
        this.dispatcher = dispatcher;
        this.wrapperFactory = wrapperFactory;
    }

    @Override
    public ResultSetProxyLogic create(ResultSet resultSet, ConnectionInfo connectionInfo, ProxyConfig proxyConfig) {
        var ctx = PENDING_QUERY.get();
        PENDING_QUERY.remove(); // Consume — afterQuery will not find it (by design).

        if (ctx == null) {
            return ResultSetProxyLogicFactory.DEFAULT.create(resultSet, connectionInfo, proxyConfig);
        }

        var sql = ctx.sql() == null ? "" : ctx.sql();
        var parameterMetadata = ctx.parameterMetadata() != null
                ? ctx.parameterMetadata()
                : List.<io.github.rroyoo.mockjdbc.mock.ParameterMetadata>of();
        var startTimeMs = ctx.startTimeMs();
        var datasourceIdSnapshot = datasourceId;
        var dispatcherRef = dispatcher;

        return ByteBuddyResultSetWrapperFactory.capturingLogic(resultSet,
                serialized -> {
                    var elapsedMs = System.currentTimeMillis() - startTimeMs;
                    var mockedQuery = JdbcQueryCaptureListener.buildMockedQuery(
                            sql,
                            parameterMetadata,
                            serialized,
                            null,
                            elapsedMs,
                            true,
                            datasourceIdSnapshot,
                            0L,
                            serialized.getRowsCount()
                    );
                    dispatcherRef.publish(mockedQuery);
                },
                wrapperFactory.rowBatchSize()
        );
    }
}

