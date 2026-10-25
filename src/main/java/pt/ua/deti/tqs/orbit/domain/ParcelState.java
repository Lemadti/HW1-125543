package pt.ua.deti.tqs.orbit.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Parcel states, as in the state machine diagram (StateMachine/StateMachineDiagram.png).
 */
public enum ParcelState {
    CREATED,
    CANCELLED,
    ON_ITS_WAY,
    READY_FOR_PICKUP,
    PICKED_UP,
    EXPIRED,
    TO_RETURN,
    RETURNED;

    /** States in which the parcel holds a reservation on its compartment (the compartment is not allocatable). */
    public static final Set<ParcelState> RESERVING = EnumSet.of(CREATED, ON_ITS_WAY, READY_FOR_PICKUP);

    /** States in which the parcel is physically inside the compartment and can still be collected by the recipient. */
    public static final Set<ParcelState> COLLECTABLE = EnumSet.of(READY_FOR_PICKUP, EXPIRED, TO_RETURN);

    public boolean isTerminal() {
        return this == CANCELLED || this == PICKED_UP || this == RETURNED;
    }
}
