package pt.ua.deti.tqs.orbit.adapter.notification;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import pt.ua.deti.tqs.orbit.port.Notification;

/** Bounded in-memory list of the last notifications (exposed as the "demo inbox" when demo mode is on). */
public class NotificationOutbox {

    public record Entry(Instant sentAt, Notification notification) {
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public NotificationOutbox(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(Instant sentAt, Notification n) {
        entries.addFirst(new Entry(sentAt, n));
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    public synchronized List<Entry> latest() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
