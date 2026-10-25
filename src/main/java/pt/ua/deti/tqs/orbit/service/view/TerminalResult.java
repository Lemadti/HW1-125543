package pt.ua.deti.tqs.orbit.service.view;

import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Outcome of a locker terminal interaction.
 *
 * @param outcome              DOOR_OPENED, DEPOSITED, COMPLETED, DOOR_DID_NOT_OPEN, DOOR_DID_NOT_CLOSE,
 *                             REPLACEMENT_ISSUED or NO_REPLACEMENT_AVAILABLE
 * @param message              text to show on the terminal
 * @param trackingCode         the parcel
 * @param state                parcel state after the interaction
 * @param compartmentCode      the compartment involved
 * @param sessionToken         token to confirm the door closed or ask for a replacement (null when the interaction ended)
 * @param replacementAvailable for door failures during drop-off: whether another compartment of the same size is free
 * @param assistancePhone      phone number to call for help
 */
public record TerminalResult(String outcome, String message, String trackingCode, ParcelState state,
                             String compartmentCode, String sessionToken, Boolean replacementAvailable,
                             String assistancePhone) {
}
