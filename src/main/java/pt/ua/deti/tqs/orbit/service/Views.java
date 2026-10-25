package pt.ua.deti.tqs.orbit.service;

import java.util.List;
import pt.ua.deti.tqs.orbit.domain.Contact;
import pt.ua.deti.tqs.orbit.domain.Incident;
import pt.ua.deti.tqs.orbit.domain.LocationScan;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelStateMachine;
import pt.ua.deti.tqs.orbit.domain.ParcelTransition;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.service.view.IncidentView;
import pt.ua.deti.tqs.orbit.service.view.LocationView;
import pt.ua.deti.tqs.orbit.service.view.ParcelDetail;
import pt.ua.deti.tqs.orbit.service.view.ParcelSummary;
import pt.ua.deti.tqs.orbit.service.view.TrackingView;
import pt.ua.deti.tqs.orbit.service.view.TransitionView;

/** Entity -> view mapping (inside transactions; open-in-view is disabled). */
final class Views {

    private Views() {
    }

    /**
     * Maps a history entry.
     *
     * @param t the transition
     * @return the view
     */
    static TransitionView transition(ParcelTransition t) {
        return new TransitionView(t.getFromState(), t.getToState(), t.getEvent(), t.getActor(), t.getOccurredAt(),
                t.getNote());
    }

    /**
     * Maps a location scan.
     *
     * @param l the scan
     * @return the view
     */
    static LocationView location(LocationScan l) {
        return new LocationView(l.getLabel(), l.getLatitude(), l.getLongitude(), l.getScannedAt());
    }

    /**
     * Public tracking view (recipient name masked).
     *
     * @param p the parcel
     * @return the view
     */
    static TrackingView tracking(Parcel p) {
        Station s = p.getStation();
        return new TrackingView(p.getTrackingCode(), p.getState(), s.getCode(), s.getName(),
                s.getAddress() + ", " + s.getPostalCode() + " " + s.getCity(), s.getLatitude(), s.getLongitude(),
                p.getCompartment().getCode(), p.getSize(), p.getRecipient().maskedName(), p.getCreatedAt(),
                p.getLeaseStart(), p.getLeaseHours(), p.getDropOffDeadline(), p.getDepositedAt(),
                p.getPickupDeadline(), p.lastLocation().map(Views::location).orElse(null),
                p.getLocations().stream().map(Views::location).toList(),
                p.getHistory().stream().map(Views::transition).toList(),
                List.copyOf(ParcelStateMachine.allowedEvents(p.getState())));
    }

    /**
     * Full operator view.
     *
     * @param p the parcel
     * @return the view
     */
    static ParcelDetail detail(Parcel p) {
        return new ParcelDetail(p.getTrackingCode(), p.getState(), p.getStation().getCode(),
                p.getStation().getName(), p.getCompartment().getCode(), p.getSize(), contact(p.getSender()),
                contact(p.getRecipient()), p.getCreatedAt(), p.getLeaseStart(), p.getLeaseHours(), p.getPrice(),
                p.getDropOffDeadline(), p.getDepositedAt(), p.getPickupDeadline(),
                p.lastLocation().map(Views::location).orElse(null),
                p.getHistory().stream().map(Views::transition).toList(),
                List.copyOf(ParcelStateMachine.allowedEvents(p.getState())));
    }

    /**
     * Row of the operator lists.
     *
     * @param p the parcel
     * @return the view
     */
    static ParcelSummary summary(Parcel p) {
        return new ParcelSummary(p.getTrackingCode(), p.getState(), p.getStation().getCode(),
                p.getStation().getName(), p.getCompartment().getCode(), p.getSender().getName(),
                p.getRecipient().getName(), p.getCreatedAt(), p.getDropOffDeadline(), p.getPickupDeadline());
    }

    /**
     * Maps an incident.
     *
     * @param i the incident
     * @return the view
     */
    static IncidentView incident(Incident i) {
        return new IncidentView(i.getId(), i.getCompartment().getCode(), i.getTrackingCode(), i.getType(),
                i.getReportedBy(), i.getReportedAt(), i.getDescription());
    }

    private static ParcelDetail.ContactView contact(Contact c) {
        return new ParcelDetail.ContactView(c.getName(), c.getEmail(), c.getPhone());
    }
}
