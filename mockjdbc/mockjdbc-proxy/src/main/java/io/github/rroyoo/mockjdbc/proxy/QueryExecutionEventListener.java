package io.github.rroyoo.mockjdbc.proxy;

public interface QueryExecutionEventListener {

    void onQueryExecutionEvent(QueryExecutionEvent event);
}
