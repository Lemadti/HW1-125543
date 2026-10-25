package pt.ua.deti.tqs.orbit.adapter.notification;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import pt.ua.deti.tqs.orbit.port.Notification;

/** Bounded in-memory list of the last notifications (exposed as the "demo inbox" when demo mode is on). */
public class NotificationOutbox {

    /**
     * A stored notification.
     *
     * @param sentAt       when it was sent
     * @param notification the notification
     */
    public record Entry(Instant sentAt, Notification notification) {
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Creates an empty outbox.
     *
     * @param capacity maximum number of notifications kept (the oldest are dropped)
     */
    public NotificationOutbox(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Stores a notification as the newest entry.
     *
     * @param sentAt when it was sent
     * @param n      the notification
     */
    public synchronized void add(Instant sentAt, Notification n) {
        entries.addFirst(new Entry(sentAt, n));
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    /**
     * The stored notifications, newest first.
     *
     * @return a copy of the entries
     */
    public synchronized List<Entry> latest() {
        return new ArrayList<>(entries);
    }

    /** Removes every stored notification. */
    public synchronized void clear() {
        entries.clear();
    }
}
