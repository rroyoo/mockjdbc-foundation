package io.github.rroyoo.mockjdbc.proxy;

public record AsyncDispatchStats(long offered,
                                 long sent,
                                 long dropped,
                                 long failed,
                                 int queued) {
}

