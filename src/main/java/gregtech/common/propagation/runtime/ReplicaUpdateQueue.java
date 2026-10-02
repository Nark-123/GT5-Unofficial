package gregtech.common.propagation.runtime;

import java.util.ArrayDeque;

final class ReplicaUpdateQueue {

    private final ArrayDeque<Runnable> queue =
        new ArrayDeque<>();

    public synchronized void enqueue(
        Runnable update) {

        if (update == null) {
            throw new IllegalArgumentException(
                "Replica update is null");
        }

        queue.addLast(update);
    }

    public void drain() {
        ArrayDeque<Runnable> pending;

        synchronized (this) {
            if (queue.isEmpty()) {
                return;
            }

            pending =
                new ArrayDeque<>(queue);

            queue.clear();
        }

        Runnable update;

        while ((update = pending.pollFirst())
            != null) {

            update.run();
        }
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized int size() {
        return queue.size();
    }
}
