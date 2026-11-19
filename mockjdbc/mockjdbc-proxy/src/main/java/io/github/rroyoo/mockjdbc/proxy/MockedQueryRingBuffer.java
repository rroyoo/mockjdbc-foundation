package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Fixed-size, array-backed, lock-free multi-producer/multi-consumer circular buffer of
 * {@link MockedQuery} events.
 *
 * <p>Implements the bounded MPMC queue algorithm described by Dmitry Vyukov: each slot carries
 * its own sequence counter so producers and consumers can claim slots with a single CAS on a
 * shared cursor, without a global lock and without allocating a node per element (unlike
 * {@code LinkedBlockingDeque}).
 *
 * <p><strong>Correctness note:</strong> a slot's data is written with a plain array store,
 * followed by a release-store of that slot's sequence number. Consumers acquire-load the
 * sequence before reading the slot's data. The Java Memory Model guarantees that once a consumer
 * observes the published sequence value via {@link AtomicLongArray#get(int)}, it also observes
 * the preceding plain write to the data array — the same technique used internally by the LMAX
 * Disruptor's {@code RingBuffer} entries array.
 *
 * <p>This buffer never blocks: {@link #tryOffer} and {@link #tryPoll} return immediately when
 * full or empty, respectively. Overflow handling (drop-oldest / drop-newest) is the caller's
 * responsibility.
 */
final class MockedQueryRingBuffer {

    private final MockedQuery[] buffer;
    private final AtomicLongArray sequence;
    private final int mask;
    private final int capacity;

    private final AtomicLong writeCursor = new AtomicLong(0);
    private final AtomicLong readCursor = new AtomicLong(0);

    /**
     * @param requestedCapacity minimum capacity; rounded up to the next power of two (floor of 2 —
     *                          the underlying MPMC slot-sequence algorithm requires at least 2
     *                          slots to distinguish "just published" from "consumed, ready for
     *                          next lap" on the same physical slot)
     */
    MockedQueryRingBuffer(int requestedCapacity) {
        var normalized = Math.max(2, requestedCapacity);
        this.capacity = Integer.highestOneBit(normalized - 1) << 1;
        this.mask = capacity - 1;
        this.buffer = new MockedQuery[capacity];
        this.sequence = new AtomicLongArray(capacity);
        for (var i = 0; i < capacity; i++) {
            sequence.set(i, i);
        }
    }

    int capacity() {
        return capacity;
    }

    /**
     * Attempts to publish {@code event} without overwriting existing data.
     *
     * @return {@code true} if published; {@code false} if the buffer is full
     */
    boolean tryOffer(MockedQuery event) {
        while (true) {
            var pos = writeCursor.get();
            var idx = (int) (pos & mask);
            var seq = sequence.get(idx);
            var diff = seq - pos;

            if (diff == 0) {
                if (writeCursor.compareAndSet(pos, pos + 1)) {
                    buffer[idx] = event;
                    sequence.set(idx, pos + 1);
                    return true;
                }
                // Lost the race for this slot; retry with a fresh cursor read.
            } else if (diff < 0) {
                return false; // buffer full — slot not yet consumed
            }
            // diff > 0: another producer already advanced past this slot; retry.
        }
    }

    /**
     * Attempts to remove and return the oldest published event.
     *
     * @return the event, or {@code null} if the buffer is empty
     */
    MockedQuery tryPoll() {
        while (true) {
            var pos = readCursor.get();
            var idx = (int) (pos & mask);
            var seq = sequence.get(idx);
            var diff = seq - (pos + 1);

            if (diff == 0) {
                if (readCursor.compareAndSet(pos, pos + 1)) {
                    var event = buffer[idx];
                    buffer[idx] = null;
                    sequence.set(idx, pos + capacity);
                    return event;
                }
                // Lost the race for this slot; retry with a fresh cursor read.
            } else if (diff < 0) {
                return null; // buffer empty — nothing published for this slot yet
            }
            // diff > 0: another consumer already advanced past this slot; retry.
        }
    }

    /** Approximate number of queued events (may be stale under concurrent access). */
    int size() {
        var queued = writeCursor.get() - readCursor.get();
        return (int) Math.max(0, queued);
    }
}
