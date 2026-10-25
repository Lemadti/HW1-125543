package pt.ua.deti.tqs.orbit.domain;

/** Raised when a lifecycle event is not valid in the parcel's current state (or for that actor). */
public class InvalidTransitionException extends RuntimeException {

    private final transient ParcelState currentState;
    private final transient ParcelEvent event;
    private final transient Actor actor;

    /**
     * Creates the exception.
     *
     * @param currentState the parcel state when the event was attempted
     * @param event        the rejected event
     * @param actor        who attempted it
     * @param message      explanation
     */
    public InvalidTransitionException(ParcelState currentState, ParcelEvent event, Actor actor, String message) {
        super(message);
        this.currentState = currentState;
        this.event = event;
        this.actor = actor;
    }

    /**
     * Returns the current state.
     *
     * @return the current state
     */
    public ParcelState getCurrentState() {
        return currentState;
    }

    /**
     * Returns the event.
     *
     * @return the event
     */
    public ParcelEvent getEvent() {
        return event;
    }

    /**
     * Returns the actor.
     *
     * @return the actor
     */
    public Actor getActor() {
        return actor;
    }
}
