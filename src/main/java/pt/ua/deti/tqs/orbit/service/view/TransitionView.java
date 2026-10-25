package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * One entry of the parcel history.
 *
 * @param from  previous state, or null for the registration
 * @param to    new state
 * @param event the event, or null for the registration
 * @param actor who triggered it
 * @param at    when it happened
 * @param note  optional note
 */
public record TransitionView(ParcelState from, ParcelState to, ParcelEvent event, Actor actor, Instant at,
                                String note) {
}
