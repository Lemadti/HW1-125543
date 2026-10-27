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

    /** Saturday 24 October 2026, 09:00 in Lisbon (summer time). */
    public static final Instant T0 = Instant.parse("2026-10-24T08:00:00Z");
    /**
     * Drop-off deadline of a parcel allocated at {@link #T0}: 10 working days later, Friday 6 November 2026 at 09:00
     * in Lisbon (winter time, so 09:00 UTC). Worked out by hand: the weekend days and the DST change are skipped.
     */
    public static final Instant DROP_OFF_DEADLINE = Instant.parse("2026-11-06T09:00:00Z");
    public static final Contact SENDER = new Contact("Ana Silva", "ana@example.com", "+351 912 345 678");
    public static final Contact RECIPIENT = new Contact("João Santos", "joao@example.com", "+351 934 567 890");

    private TestData() {
    }

    public static Station station(long id, String code, double lat, double lon) {
        Station s = new Station(code, "Station " + code, "Rua 1", "3810-001", "Aveiro", lat, lon, null);
        setId(s, id);
        return s;
    }

    public static Compartment compartment(long id, Station station, String code, CompartmentSize size) {
        Compartment c = new Compartment(station, code, Integer.parseInt(code.substring(2)), size);
        setId(c, id);
        return c;
    }

    public static Parcel parcel(long id, String trackingCode, Compartment compartment, String dropOffHash) {
        Parcel p = Parcel.register(trackingCode, "DLV-" + trackingCode, compartment, SENDER, RECIPIENT,
                DROP_OFF_DEADLINE, new BigDecimal("3.00"), dropOffHash, T0);
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
