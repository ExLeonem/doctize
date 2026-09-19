package dev.example.cache;

import dev.doctize.annotations.Doc;

/**
 * Decides which entry leaves a full cache.
 *
 * @since 1.0
 */
@Doc(order = 3)
public enum EvictionPolicy {

    /** Evicts the entry that was accessed longest ago. A good default for most workloads. */
    LRU,

    /** Evicts the entry that was accessed least often. Keeps hot entries through bursts of one-off reads. */
    LFU,

    /** Evicts the entry that was written first, regardless of how often it is read. */
    FIFO
}
