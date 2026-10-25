package pt.ua.deti.tqs.orbit.adapter.notification;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.orbit.port.NotifierPort;

/** Wires the notifier port to the log notifier and its outbox. */
@Configuration
public class NotificationConfig {

    /** Creates the notification configuration. */
    public NotificationConfig() {
        // no state
    }

    /**
     * In-memory outbox keeping the last 200 notifications.
     *
     * @return the outbox
     */
    @Bean
    NotificationOutbox notificationOutbox() {
        return new NotificationOutbox(200);
    }

    /**
     * The notifier port implementation: log + outbox.
     *
     * @param outbox the outbox
     * @param clock  time source
     * @return the notifier
     */
    @Bean
    NotifierPort notifierPort(NotificationOutbox outbox, Clock clock) {
        return new LogNotifier(outbox, clock);
    }
}
