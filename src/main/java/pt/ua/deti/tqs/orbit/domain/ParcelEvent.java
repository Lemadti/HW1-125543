package pt.ua.deti.tqs.orbit.domain;

import java.util.EnumSet;
import java.util.Set;

/** Events that move a parcel through its lifecycle, with the actors allowed to trigger each one. */
public enum ParcelEvent {
    /** Partner (or recipient) cancels a parcel that was not yet collected by the courier. */
    CANCEL(EnumSet.of(Actor.PARTNER, Actor.RECIPIENT)),
    /** The drop-off deadline (end of the lease) passed before the courier collected the parcel. */
    DROP_OFF_DEADLINE_PASSED(EnumSet.of(Actor.SYSTEM)),
    /** Courier scans the delivery code (QR) at the partner: the parcel is in transit. */
    COLLECT(EnumSet.of(Actor.COURIER)),
    /** Courier deposited the parcel and the door was confirmed closed. */
    DEPOSIT(EnumSet.of(Actor.COURIER)),
    /** Recipient entered a valid pickup code and the compartment opened. */
    PICK_UP(EnumSet.of(Actor.RECIPIENT)),
    /** The pickup window passed. */
    PICKUP_DEADLINE_PASSED(EnumSet.of(Actor.SYSTEM)),
    /** An expired parcel is flagged to be returned to the partner (automatically when its compartment is re-allocated). */
    MARK_TO_RETURN(EnumSet.of(Actor.SYSTEM, Actor.OPERATOR)),
    /** The parcel was physically removed from the compartment (by the courier on the next drop-off, or by an operator). */
    REMOVE(EnumSet.of(Actor.COURIER, Actor.OPERATOR));

    private final Set<Actor> allowedActors;

    ParcelEvent(Set<Actor> allowedActors) {
        this.allowedActors = allowedActors;
    }

    public boolean allows(Actor actor) {
        return allowedActors.contains(actor);
    }

    public Set<Actor> allowedActors() {
        return EnumSet.copyOf(allowedActors);
    }
}
