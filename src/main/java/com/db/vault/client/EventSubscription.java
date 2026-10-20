package com.db.vault.client;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Follows the event store from a position, on one background thread.
 *
 * <ul>
 *   <li>In order: events are delivered oldest first, one at a time.</li>
 *   <li>At least once: if the handler throws, the same event is retried after a
 *       pause; it is never skipped. Make the handler idempotent (the event id helps).</li>
 *   <li>Resumable: store {@link #lastEventId()} after processing and pass it to
 *       {@link Builder#from(long)} on restart.</li>
 * </ul>
 *
 * <pre>{@code
 * try (EventSubscription sub = store.subscribe()
 *         .from(checkpoint.load())
 *         .filter(EventQuery.from(0).ofEventType(Event.STATE_CHANGED))
 *         .onError(e -> log.warn("Event handling failed", e))
 *         .start(event -> {
 *             handle(event);
 *             checkpoint.save(event.id());
 *         })) {
 *     ...
 * }
 * }</pre>
 */
public final class EventSubscription implements AutoCloseable {

    private final AtomicLong lastId;
    private final Thread thread;
    private volatile boolean running = true;

    private EventSubscription(Builder b, Consumer<Event> handler) {
        this.lastId = new AtomicLong(b.from);
        this.thread = new Thread(() -> run(b, handler), "vault-events");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** The id of the last event the handler processed successfully. */
    public long lastEventId() {
        return lastId.get();
    }

    public boolean isRunning() {
        return running && thread.isAlive();
    }

    /** Stops polling and waits up to five seconds for the current event to finish. */
    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(Builder b, Consumer<Event> handler) {
        Duration errorPause = b.pollInterval;
        while (running) {
            try {
                List<Event> page = b.client.events(b.filter.after(lastId.get()));
                for (Event event : page) {
                    if (!running) {
                        return;
                    }
                    deliver(b, handler, event);
                    lastId.set(event.id());
                }
                errorPause = b.pollInterval;
                if (page.size() < b.filter.limit()) {
                    sleep(b.pollInterval);
                }
            } catch (VaultClientException e) {
                if (!running) {
                    return;
                }
                b.onError.accept(e);
                sleep(errorPause);
                errorPause = longer(errorPause);
            }
        }
    }

    private void deliver(Builder b, Consumer<Event> handler, Event event) {
        Duration pause = b.pollInterval;
        while (running) {
            try {
                handler.accept(event);
                return;
            } catch (RuntimeException e) {
                b.onError.accept(e);
                sleep(pause);
                pause = longer(pause);
            }
        }
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            running = false;
            Thread.currentThread().interrupt();
        }
    }

    private static Duration longer(Duration d) {
        Duration next = d.multipliedBy(2);
        return next.compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : next;
    }

    public static final class Builder {
        private final VaultClient client;
        private long from;
        private EventQuery filter = EventQuery.from(0);
        private Duration pollInterval = Duration.ofSeconds(2);
        private Consumer<Throwable> onError = e -> { };

        Builder(VaultClient client) {
            this.client = client;
        }

        /** Start after this event id (0 = from the beginning). */
        public Builder from(long afterEventId) {
            this.from = afterEventId;
            return this;
        }

        /** Only events matching this filter (document, actor, event type). Its afterId is ignored. */
        public Builder filter(EventQuery filter) {
            this.filter = Objects.requireNonNull(filter);
            return this;
        }

        /** How long to wait when caught up (default 2 seconds). */
        public Builder pollInterval(Duration interval) {
            this.pollInterval = interval;
            return this;
        }

        /** Called for fetch failures and handler exceptions; polling continues. */
        public Builder onError(Consumer<Throwable> handler) {
            this.onError = Objects.requireNonNull(handler);
            return this;
        }

        public EventSubscription start(Consumer<Event> handler) {
            return new EventSubscription(this, Objects.requireNonNull(handler));
        }
    }
}
