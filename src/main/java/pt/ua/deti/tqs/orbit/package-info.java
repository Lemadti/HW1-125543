/**
 * Orbit Lockers: a smart parcel locker network for the Aveiro zone (Portugal).
 *
 * <p>The application follows a ports-and-adapters layout:</p>
 * <ul>
 *   <li>{@link pt.ua.deti.tqs.orbit.domain}: entities and the parcel state machine (no framework logic);</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.port}: interfaces owned by the domain for hardware, geocoding and notifications;</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.adapter}: implementations of the ports (fake hardware, Nominatim, log notifier);</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.repository}: Spring Data JPA repositories (the persistence port);</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.service}: use cases and business rules;</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.web}: REST API and Thymeleaf pages;</li>
 *   <li>{@link pt.ua.deti.tqs.orbit.config} and {@link pt.ua.deti.tqs.orbit.demo}: configuration and demo helpers.</li>
 * </ul>
 */
package pt.ua.deti.tqs.orbit;
