package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.Incident;
import pt.ua.deti.tqs.orbit.domain.IncidentType;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;
import pt.ua.deti.tqs.orbit.repository.IncidentRepository;

/** Records a malfunction, optionally puts the compartment out of service, and alerts the operator. */
@Component
public class IncidentReporter {

    private static final Logger log = LoggerFactory.getLogger(IncidentReporter.class);

    private final IncidentRepository incidents;
    private final NotifierPort notifier;
    private final Clock clock;

    public IncidentReporter(IncidentRepository incidents, NotifierPort notifier, Clock clock) {
        this.incidents = incidents;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void report(Compartment compartment, String trackingCode, IncidentType type, Actor reportedBy,
                       String description, boolean putOutOfService) {
        if (putOutOfService) {
            compartment.putOutOfService(type + ": " + description);
        }
        incidents.save(new Incident(compartment, trackingCode, type, reportedBy, clock.instant(), description));
        String where = compartment.getStation().getCode() + "/" + compartment.getCode();
        log.warn("INCIDENT {} at {} parcel={} outOfService={} - {}", type, where, trackingCode, putOutOfService,
                description);
        notifier.send(new Notification(Notification.Type.OPERATOR_ALERT, trackingCode, "Orbit operations",
                "ops@orbit.example", "+48221002030", "Malfunction at " + where + ": " + type,
                description + (putOutOfService ? " Compartment put out of service." : "")));
    }
}
