package pt.ua.deti.tqs.orbit.adapter.notification;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;

/**
 * Notifier adapter: logs the notification (without its body, which may contain a secret code) and keeps it in the
 * outbox. Real SMS / e-mail are out of scope.
 */
public class LogNotifier implements NotifierPort {

    private static final Logger log = LoggerFactory.getLogger("orbit.notifications");

    private final NotificationOutbox outbox;
    private final Clock clock;

    public LogNotifier(NotificationOutbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void send(Notification n) {
        log.info("NOTIFY {} parcel={} to={} <{}> / {} subject=\"{}\"", n.type(), n.trackingCode(), n.toName(),
                maskEmail(n.toEmail()), maskPhone(n.toPhone()), n.subject());
        outbox.add(clock.instant(), n);
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 3);
    }
}
