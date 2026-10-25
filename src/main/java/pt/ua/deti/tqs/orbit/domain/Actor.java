package pt.ua.deti.tqs.orbit.domain;

/** Who triggered a lifecycle event. Recorded on every transition. */
public enum Actor {
    PARTNER,
    RECIPIENT,
    COURIER,
    OPERATOR,
    SYSTEM
}
