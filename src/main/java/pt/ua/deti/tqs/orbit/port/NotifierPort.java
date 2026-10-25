package pt.ua.deti.tqs.orbit.port;

/** Port for outgoing notifications (no real SMS/e-mail in scope). */
public interface NotifierPort {

    /**
     * Sends (or, in this project, logs) a notification.
     *
     * @param notification the message and its recipient
     */
    void send(Notification notification);
}
