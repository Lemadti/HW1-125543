package pt.ua.deti.tqs.orbit.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The parcel lifecycle as an explicit transition table: (state, event) -> new state.
 * Pure Java (no Spring, no persistence) so that every rule can be unit tested exhaustively.
 */
public final class ParcelStateMachine {

    private static final Map<ParcelState, Map<ParcelEvent, ParcelState>> TABLE = new EnumMap<>(ParcelState.class);

    static {
        for (ParcelState s : ParcelState.values()) {
            TABLE.put(s, new EnumMap<>(ParcelEvent.class));
        }
        allow(ParcelState.CREATED, ParcelEvent.CANCEL, ParcelState.CANCELLED);
        allow(ParcelState.CREATED, ParcelEvent.DROP_OFF_DEADLINE_PASSED, ParcelState.CANCELLED);
        allow(ParcelState.CREATED, ParcelEvent.COLLECT, ParcelState.ON_ITS_WAY);
        allow(ParcelState.ON_ITS_WAY, ParcelEvent.DEPOSIT, ParcelState.READY_FOR_PICKUP);
        allow(ParcelState.ON_ITS_WAY, ParcelEvent.DROP_OFF_DEADLINE_PASSED, ParcelState.CANCELLED);
        allow(ParcelState.READY_FOR_PICKUP, ParcelEvent.PICK_UP, ParcelState.PICKED_UP);
        allow(ParcelState.READY_FOR_PICKUP, ParcelEvent.PICKUP_DEADLINE_PASSED, ParcelState.EXPIRED);
        allow(ParcelState.EXPIRED, ParcelEvent.PICK_UP, ParcelState.PICKED_UP);
        allow(ParcelState.EXPIRED, ParcelEvent.MARK_TO_RETURN, ParcelState.TO_RETURN);
        allow(ParcelState.TO_RETURN, ParcelEvent.PICK_UP, ParcelState.PICKED_UP);
        allow(ParcelState.TO_RETURN, ParcelEvent.REMOVE, ParcelState.RETURNED);
    }

    private ParcelStateMachine() {
    }

    private static void allow(ParcelState from, ParcelEvent event, ParcelState to) {
        TABLE.get(from).put(event, to);
    }

    /**
     * Target state for the event, if the event is valid in that state (actors are not checked).
     *
     * @param from  current state
     * @param event the event
     * @return the new state, or empty if the event is not valid in {@code from}
     */
    public static Optional<ParcelState> target(ParcelState from, ParcelEvent event) {
        return Optional.ofNullable(TABLE.get(from).get(event));
    }

    /**
     * Events accepted in the given state.
     *
     * @param from the state
     * @return the accepted events (empty for terminal states)
     */
    public static Set<ParcelEvent> allowedEvents(ParcelState from) {
        return Collections.unmodifiableSet(TABLE.get(from).keySet());
    }

    /**
     * Validates the event and the actor, returning the new state.
     *
     * @param from  current state
     * @param event the event
     * @param actor who triggers it
     * @return the new state
     * @throws InvalidTransitionException if the event is not valid in the current state, or the actor may not trigger it
     */
    public static ParcelState next(ParcelState from, ParcelEvent event, Actor actor) {
        ParcelState to = target(from, event)
                .orElseThrow(() -> new InvalidTransitionException(from, event, actor,
                        "Event " + event + " is not allowed in state " + from));
        if (!event.allows(actor)) {
            throw new InvalidTransitionException(from, event, actor,
                    "Actor " + actor + " may not trigger " + event);
        }
        return to;
    }
}
