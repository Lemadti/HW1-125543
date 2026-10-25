package pt.ua.deti.tqs.orbit.service.view;

import pt.ua.deti.tqs.orbit.domain.CompartmentSize;

/**
 * Number of free compartments per size.
 *
 * @param s free small compartments
 * @param m free medium compartments
 * @param l free large compartments
 */
public record SizeCounts(long s, long m, long l) {

    /** No free compartment. */
    public static final SizeCounts NONE = new SizeCounts(0, 0, 0);

    /**
     * Free compartments of any size.
     *
     * @return the sum
     */
    public long total() {
        return s + m + l;
    }

    /**
     * Free compartments of one size.
     *
     * @param size the size
     * @return the count
     */
    public long of(CompartmentSize size) {
        return switch (size) {
            case S -> s;
            case M -> m;
            case L -> l;
        };
    }

    /**
     * A copy with the count of one size replaced.
     *
     * @param size  the size
     * @param count the new count
     * @return the copy
     */
    public SizeCounts with(CompartmentSize size, long count) {
        return switch (size) {
            case S -> new SizeCounts(count, m, l);
            case M -> new SizeCounts(s, count, l);
            case L -> new SizeCounts(s, m, count);
        };
    }
}
