package pt.ua.deti.tqs.orbit.domain;

/** Kinds of malfunction reported to the operator. */
public enum IncidentType {
    /** The lock did not release when the terminal asked to open the door. */
    DOOR_DID_NOT_OPEN,
    /** The door sensor still reported "open" when the user said the door was closed. */
    DOOR_DID_NOT_CLOSE,
    /** The station controller did not answer the hardware port. */
    HARDWARE_UNREACHABLE,
    /** An operator put the compartment out of service by hand. */
    MANUAL_OUT_OF_SERVICE
}
