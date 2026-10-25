package pt.ua.deti.tqs.orbit.port;

/**
 * A message to a person (sent by e-mail and SMS in production; logged here).
 *
 * @param type      what the message is about (e.g. PICKUP_CODE)
 * @param toName    recipient name
 * @param toEmail   recipient e-mail
 * @param toPhone   recipient phone
 * @param subject   short subject
 * @param body      full text (may contain a code; never logged)
 */
public record Notification(Type type, String trackingCode, String toName, String toEmail, String toPhone,
                           String subject, String body) {

    public enum Type {
        REGISTRATION_CONFIRMED,
        PICKUP_CODE,
        REPLACEMENT_DROP_OFF_CODE,
        PARCEL_PICKED_UP,
        PARCEL_EXPIRED,
        PARCEL_CANCELLED,
        OPERATOR_ALERT
    }
}
