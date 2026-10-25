package pt.ua.deti.tqs.orbit.service;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Contact;

/**
 * Input of UC2 (register a parcel).
 *
 * @param stationCode     station code
 * @param size            parcel size
 * @param compartmentCode optional compartment chosen in the web front view; null for automatic choice
 * @param leaseStart      optional lease start; null means now
 * @param leaseHours      lease duration in hours
 * @param sender          sender contact
 * @param recipient       recipient contact
 */
public record RegisterParcelCommand(String stationCode, CompartmentSize size, String compartmentCode,
                                    Instant leaseStart, int leaseHours, Contact sender, Contact recipient) {
}
