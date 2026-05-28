package emergency;

import java.util.*;

/**
 * RequestQueue.java
 * FIFO queue for emergency requests.
 * Synopsis requirement: queue-based request handling using first-in-first-out.
 */
public class RequestQueue {

    // ── Singleton instance (mirrors JS module.exports = new RequestQueue()) ───
    private static final RequestQueue INSTANCE = new RequestQueue();
    public  static RequestQueue getInstance() { return INSTANCE; }

    // ── Internal queue ────────────────────────────────────────────────────────
    private final List<QueueEntry> queue = new ArrayList<>();

    // ── Entry record ──────────────────────────────────────────────────────────
    public static class QueueEntry {
        public final int    id;
        public final String severity;
        public final long   timestamp;

        public QueueEntry(int id, String severity, long timestamp) {
            this.id        = id;
            this.severity  = severity;
            this.timestamp = timestamp;
        }
    }

    // ── enqueue — append to tail (FIFO) ───────────────────────────────────────
    public synchronized void enqueue(int id, String severity) {
        queue.add(new QueueEntry(id, severity, System.currentTimeMillis()));
    }

    // ── dequeue — mirrors JS dequeue() ───────────────────────────────────────
    public synchronized QueueEntry dequeue() {
        if (queue.isEmpty()) return null;
        return queue.remove(0);
    }

    // ── peek — mirrors JS peek() ──────────────────────────────────────────────
    public synchronized QueueEntry peek() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    public synchronized boolean isEmpty() { return queue.isEmpty(); }
    public synchronized int     size()    { return queue.size(); }

    // ── getAll — mirrors JS getAll() ──────────────────────────────────────────
    public synchronized List<QueueEntry> getAll() {
        return new ArrayList<>(queue);
    }

}
