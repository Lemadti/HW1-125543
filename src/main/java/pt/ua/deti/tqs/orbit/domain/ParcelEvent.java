package pt.ua.deti.tqs.orbit.domain;

import java.util.EnumSet;
import java.util.Set;

/** Events that move a parcel through its lifecycle, with the actors allowed to trigger each one. */
public enum ParcelEvent {
    /** Partner (or recipient) cancels a parcel that was not yet collected by the courier. */
    CANCEL(EnumSet.of(Actor.PARTNER, Actor.RECIPIENT)),
    /** The drop-off deadline passed before the parcel was dropped off (whether or not the courier collected it). */
    DROP_OFF_DEADLINE_PASSED(EnumSet.of(Actor.SYSTEM)),
    /** Courier scans the delivery code (QR) at the partner: the parcel is in transit. */
    COLLECT(EnumSet.of(Actor.COURIER)),
    /** Courier deposited the parcel and the door was confirmed closed. */
    DEPOSIT(EnumSet.of(Actor.COURIER)),
    /** Recipient entered a valid pickup code and the compartment opened. */
    PICK_UP(EnumSet.of(Actor.RECIPIENT)),
    /** The pickup window passed. */
    PICKUP_DEADLINE_PASSED(EnumSet.of(Actor.SYSTEM)),
    /** An expired parcel is flagged to be returned (automatically when its compartment is re-allocated). */
    MARK_TO_RETURN(EnumSet.of(Actor.SYSTEM, Actor.OPERATOR)),
    /** The parcel was physically removed from the compartment (by the courier on the next drop-off, or by an operator). */
    REMOVE(EnumSet.of(Actor.COURIER, Actor.OPERATOR));

    private final Set<Actor> allowedActors;

    ParcelEvent(Set<Actor> allowedActors) {
        this.allowedActors = allowedActors;
    }

    /**
     * Whether the actor may trigger this event.
     *
     * @param actor who is trying to trigger the event
     * @return true if the actor is allowed
     */
    public boolean allows(Actor actor) {
        return allowedActors.contains(actor);
    }

    /**
     * The actors allowed to trigger this event.
     *
     * @return a copy of the allowed actors
     */
    public Set<Actor> allowedActors() {
        return EnumSet.copyOf(allowedActors);
    }
}
