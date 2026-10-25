package pt.ua.deti.tqs.orbit.domain;

import java.util.EnumSet;
import java.util.Set;

/** Parcel states, as in the state machine diagram (StateMachine/StateMachineDiagram.png). */
public enum ParcelState {
    /** Registered by the partner; a compartment is reserved. */
    CREATED,
    /** Cancelled by the partner or recipient, or because the lease ended before collection (terminal state). */
    CANCELLED,
    /** Collected by the courier and in transit to the station. */
    ON_ITS_WAY,
    /** Deposited in the compartment; the recipient has the pickup code. */
    READY_FOR_PICKUP,
    /** Collected by the recipient (terminal state). */
    PICKED_UP,
    /** The pickup window ended; the parcel is still inside and can still be collected. */
    EXPIRED,
    /** Expired and flagged to be returned to the partner; still collectable until removed. */
    TO_RETURN,
    /** Removed from the compartment and returned to the partner (terminal state). */
    RETURNED;

    /** States in which the parcel holds a reservation on its compartment (the compartment is not allocatable). */
    public static final Set<ParcelState> RESERVING = EnumSet.of(CREATED, ON_ITS_WAY, READY_FOR_PICKUP);

    /** States in which the parcel is physically inside the compartment and can still be collected by the recipient. */
    public static final Set<ParcelState> COLLECTABLE = EnumSet.of(READY_FOR_PICKUP, EXPIRED, TO_RETURN);

    /**
     * Whether no further event is accepted in this state.
     *
     * @return true for CANCELLED, PICKED_UP and RETURNED
     */
    public boolean isTerminal() {
        return this == CANCELLED || this == PICKED_UP || this == RETURNED;
    }
}
