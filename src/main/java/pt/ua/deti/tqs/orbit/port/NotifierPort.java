package pt.ua.deti.tqs.orbit.port;

/** Port for outgoing notifications (no real SMS/e-mail in scope). */
public interface NotifierPort {

    void send(Notification notification);
}
