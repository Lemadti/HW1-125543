package pt.ua.deti.tqs.orbit.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;

/** Fake notifier for unit tests: keeps every notification so tests can assert on them and read codes. */
public class RecordingNotifier implements NotifierPort {

    private static final Pattern CODE = Pattern.compile("code: (\\d{6})");

    private final List<Notification> sent = new ArrayList<>();

    @Override
    public void send(Notification notification) {
        sent.add(notification);
    }

    public List<Notification> sent() {
        return List.copyOf(sent);
    }

    public List<Notification> ofType(Notification.Type type) {
        return sent.stream().filter(n -> n.type() == type).toList();
    }

    /** The 6-digit code in the last notification of that type, if any. */
    public Optional<String> lastCode(Notification.Type type) {
        List<Notification> list = ofType(type);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = CODE.matcher(list.get(list.size() - 1).body());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
