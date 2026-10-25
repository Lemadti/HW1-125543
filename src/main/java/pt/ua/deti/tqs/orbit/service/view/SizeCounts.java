package pt.ua.deti.tqs.orbit.service.view;

import pt.ua.deti.tqs.orbit.domain.CompartmentSize;

/** Number of free compartments per size. */
public record SizeCounts(long s, long m, long l) {

    public static final SizeCounts NONE = new SizeCounts(0, 0, 0);

    public long total() {
        return s + m + l;
    }

    public long of(CompartmentSize size) {
        return switch (size) {
            case S -> s;
            case M -> m;
            case L -> l;
        };
    }

    public SizeCounts with(CompartmentSize size, long count) {
        return switch (size) {
            case S -> new SizeCounts(count, m, l);
            case M -> new SizeCounts(s, count, l);
            case L -> new SizeCounts(s, m, count);
        };
    }
}
