/**
 * Domain model: stations, compartments, parcels and their lifecycle.
 *
 * <p>The lifecycle rules live in {@link pt.ua.deti.tqs.orbit.domain.ParcelStateMachine}, a pure Java transition
 * table. A {@link pt.ua.deti.tqs.orbit.domain.Parcel} only changes state through
 * {@link pt.ua.deti.tqs.orbit.domain.Parcel#apply}, which validates the event and records the transition.</p>
 */
package pt.ua.deti.tqs.orbit.domain;
