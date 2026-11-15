package gameconsole.server.events;

import java.util.concurrent.atomic.AtomicLong;

public final class DeliveryStatistics {

    public record Snapshot(long queued, long delivered, long pending, long retries, long rejected, long failed) {
    }

    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    void eventQueued() {
        queued.incrementAndGet();
    }

    void eventDelivered() {
        delivered.incrementAndGet();
    }

    void retryScheduled() {
        retries.incrementAndGet();
    }

    void eventRejected() {
        rejected.incrementAndGet();
    }

    void eventFailed() {
        failed.incrementAndGet();
    }

    public Snapshot snapshot() {
        long queuedNow = queued.get();
        long delivered = this.delivered.get();
        long rejected = this.rejected.get();
        long failed = this.failed.get();
        return new Snapshot(queuedNow, delivered, queuedNow - delivered - rejected - failed, retries.get(), rejected, failed);
    }
}
