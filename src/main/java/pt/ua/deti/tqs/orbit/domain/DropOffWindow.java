package pt.ua.deti.tqs.orbit.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Drop-off deadline rule: the courier may drop off the parcel during a fixed number of working days (Monday to
 * Friday, in the station's time zone) after the compartment was allocated. The deadline keeps the time of day of the
 * allocation; an allocation on a weekend starts counting on the next Monday. Public holidays are not considered.
 */
public final class DropOffWindow {

    private DropOffWindow() {
    }

    /**
     * End of the drop-off window.
     *
     * @param allocatedAt when the compartment was allocated (registration time)
     * @param workingDays number of working days allowed for the drop-off
     * @param zone        time zone that defines the days (e.g. Europe/Lisbon)
     * @return the drop-off deadline (drop-off still allowed at this instant)
     */
    public static Instant deadline(Instant allocatedAt, int workingDays, ZoneId zone) {
        ZonedDateTime day = allocatedAt.atZone(zone);
        int counted = 0;
        while (counted < workingDays) {
            day = day.plusDays(1);
            if (isWorkingDay(day.getDayOfWeek())) {
                counted++;
            }
        }
        return day.toInstant();
    }

    /**
     * Whether a drop-off is still allowed.
     *
     * @param deadline the parcel's drop-off deadline
     * @param now      current time
     * @return true up to and including the deadline
     */
    public static boolean isOpen(Instant deadline, Instant now) {
        return !now.isAfter(deadline);
    }

    private static boolean isWorkingDay(DayOfWeek day) {
        return !DayOfWeek.SATURDAY.equals(day) && !DayOfWeek.SUNDAY.equals(day);
    }
}
