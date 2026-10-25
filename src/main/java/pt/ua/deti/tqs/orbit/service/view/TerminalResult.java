package pt.ua.deti.tqs.orbit.service.view;

import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Outcome of a locker terminal interaction.
 *
 * @param outcome            DOOR_OPENED, DEPOSITED, COMPLETED, DOOR_DID_NOT_OPEN, DOOR_DID_NOT_CLOSE,
 *                           REPLACEMENT_ISSUED, NO_REPLACEMENT_AVAILABLE
 * @param sessionToken       token to confirm the door closed / ask for a replacement (null when the interaction ended)
 * @param replacementAvailable for door failures during drop-off: whether another compartment of the same size is free
 */
public record TerminalResult(String outcome, String message, String trackingCode, ParcelState state,
                             String compartmentCode, String sessionToken, Boolean replacementAvailable,
                             String assistancePhone) {
}
