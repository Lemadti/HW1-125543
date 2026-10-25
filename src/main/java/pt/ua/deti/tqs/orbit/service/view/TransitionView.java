package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

public record TransitionView(ParcelState from, ParcelState to, ParcelEvent event, Actor actor, Instant at,
                             String note) {
}
