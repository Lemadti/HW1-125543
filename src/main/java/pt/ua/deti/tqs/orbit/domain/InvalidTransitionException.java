package pt.ua.deti.tqs.orbit.domain;

/** Raised when a lifecycle event is not valid in the parcel's current state (or for that actor). */
public class InvalidTransitionException extends RuntimeException {

    private final transient ParcelState currentState;
    private final transient ParcelEvent event;
    private final transient Actor actor;

    public InvalidTransitionException(ParcelState currentState, ParcelEvent event, Actor actor, String message) {
        super(message);
        this.currentState = currentState;
        this.event = event;
        this.actor = actor;
    }

    public ParcelState getCurrentState() {
        return currentState;
    }

    public ParcelEvent getEvent() {
        return event;
    }

    public Actor getActor() {
        return actor;
    }
}
