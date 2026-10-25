package pt.ua.deti.tqs.orbit.domain;

/** Who triggered a lifecycle event. It is recorded on every transition. */
public enum Actor {
    /** The online shop that sends the parcel (REST API or web registration). */
    PARTNER,
    /** The person who collects the parcel. */
    RECIPIENT,
    /** The person who carries the parcel from the partner to the locker. */
    COURIER,
    /** Network staff supervising the stations. */
    OPERATOR,
    /** The application itself (deadlines, automatic re-allocation). */
    SYSTEM
}
