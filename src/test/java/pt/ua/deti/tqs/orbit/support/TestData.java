package pt.ua.deti.tqs.orbit.support;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Contact;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.Station;

/** Builders for domain objects in unit tests (ids are set by reflection, as JPA would). */
public final class TestData {

    public static final Instant T0 = Instant.parse("2026-10-24T08:00:00Z");
    public static final Contact SENDER = new Contact("Anna Kowalska", "anna@example.com", "+48 501 234 567");
    public static final Contact RECIPIENT = new Contact("Piotr Nowak", "piotr@example.com", "+48 602 789 123");

    private TestData() {
    }

    public static Station station(long id, String code, double lat, double lon) {
        Station s = new Station(code, "Station " + code, "Street 1", "00-001", "Warsaw", lat, lon, null);
        setId(s, id);
        return s;
    }

    public static Compartment compartment(long id, Station station, String code, CompartmentSize size) {
        Compartment c = new Compartment(station, code, Integer.parseInt(code.substring(2)), size);
        setId(c, id);
        return c;
    }

    public static Parcel parcel(long id, String trackingCode, Compartment compartment, String dropOffHash) {
        Parcel p = Parcel.register(trackingCode, "DLV-" + trackingCode, compartment, SENDER, RECIPIENT, T0, 48,
                new BigDecimal("24.00"), dropOffHash, T0);
        setId(p, id);
        return p;
    }

    public static void setId(Object entity, long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
