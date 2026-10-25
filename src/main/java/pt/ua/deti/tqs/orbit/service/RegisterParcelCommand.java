package pt.ua.deti.tqs.orbit.service;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Contact;

/**
 * Input of UC2.
 *
 * @param compartmentCode optional: a specific compartment chosen in the web front view; null = automatic choice
 * @param leaseStart      optional: defaults to now
 */
public record RegisterParcelCommand(String stationCode, CompartmentSize size, String compartmentCode,
                                    Instant leaseStart, int leaseHours, Contact sender, Contact recipient) {
}
