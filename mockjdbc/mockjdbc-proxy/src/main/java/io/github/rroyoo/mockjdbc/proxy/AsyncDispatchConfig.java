package io.github.rroyoo.mockjdbc.proxy;

public record AsyncDispatchConfig(int ringBufferCapacity,
                                  int senderThreads,
                                  OverflowStrategy overflowStrategy,
                                  int rowBatchSize) {

    public AsyncDispatchConfig {
        if (ringBufferCapacity < 1) {
            throw new IllegalArgumentException("ringBufferCapacity must be >= 1");
        }
        if (senderThreads < 1) {
            throw new IllegalArgumentException("senderThreads must be >= 1");
        }
        if (rowBatchSize < 1) {
            throw new IllegalArgumentException("rowBatchSize must be >= 1");
        }
        if (overflowStrategy == null) {
            throw new IllegalArgumentException("overflowStrategy is required");
        }
    }

    public static AsyncDispatchConfig defaults() {
        return new AsyncDispatchConfig(8192, 2, OverflowStrategy.DROP_OLDEST, 512);
    }

    public enum OverflowStrategy {
        DROP_OLDEST,
        DROP_NEWEST
    }
}

