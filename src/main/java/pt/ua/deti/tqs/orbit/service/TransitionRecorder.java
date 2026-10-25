package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelTransition;

/** Single entry point for lifecycle transitions: applies the event with the current time and logs it. */
@Component
public class TransitionRecorder {

    private static final Logger log = LoggerFactory.getLogger("orbit.lifecycle");

    private final Clock clock;

    public TransitionRecorder(Clock clock) {
        this.clock = clock;
    }

    public ParcelTransition apply(Parcel parcel, ParcelEvent event, Actor actor, String note) {
        ParcelTransition t = parcel.apply(event, actor, clock.instant(), note);
        log.info("TRANSITION parcel={} {} -> {} event={} actor={}{}", parcel.getTrackingCode(), t.getFromState(),
                t.getToState(), event, actor, note == null ? "" : " note=\"" + note + "\"");
        return t;
    }
}
