package pt.ua.deti.tqs.orbit.port;

/**
 * A message to a person (sent by e-mail and SMS in production; logged here).
 *
 * @param type         what the message is about (e.g. PICKUP_CODE)
 * @param trackingCode parcel the message refers to
 * @param toName       recipient name
 * @param toEmail      recipient e-mail
 * @param toPhone      recipient phone
 * @param subject      short subject
 * @param body         full text (may contain a code; never logged)
 */
public record Notification(Type type, String trackingCode, String toName, String toEmail, String toPhone,
                            String subject, String body) {

    /** What a notification is about. */
    public enum Type {
        /** Sent to the sender when a parcel is registered. */
        REGISTRATION_CONFIRMED,
        /** Sent to the recipient with the pickup code when the parcel is deposited. */
        PICKUP_CODE,
        /** Sent to the sender with a new drop-off code after a compartment change. */
        REPLACEMENT_DROP_OFF_CODE,
        /** Sent to the sender when the recipient collects the parcel. */
        PARCEL_PICKED_UP,
        /** Sent when the pickup window ends or an expired parcel is returned. */
        PARCEL_EXPIRED,
        /** Sent to the sender when the reservation is cancelled. */
        PARCEL_CANCELLED,
        /** Sent to operations when a malfunction is reported. */
        OPERATOR_ALERT
    }
}
