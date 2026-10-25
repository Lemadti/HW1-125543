package pt.ua.deti.tqs.orbit.adapter.notification;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.orbit.port.NotifierPort;

@Configuration
public class NotificationConfig {

    @Bean
    NotificationOutbox notificationOutbox() {
        return new NotificationOutbox(200);
    }

    @Bean
    NotifierPort notifierPort(NotificationOutbox outbox, Clock clock) {
        return new LogNotifier(outbox, clock);
    }
}
