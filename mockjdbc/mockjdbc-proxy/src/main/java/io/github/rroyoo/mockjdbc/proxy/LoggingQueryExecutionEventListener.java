package io.github.rroyoo.mockjdbc.proxy;

import java.util.logging.Logger;

public final class LoggingQueryExecutionEventListener implements QueryExecutionEventListener {

    private final Logger logger = Logger.getLogger(LoggingQueryExecutionEventListener.class.getName());

    @Override
    public void onQueryExecutionEvent(QueryExecutionEvent event) {
        logger.info(event.toString());
    }
}
