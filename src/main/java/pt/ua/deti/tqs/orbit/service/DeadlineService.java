package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;

/**
 * Time-based rules, evaluated against the injected {@link Clock} (tests move the clock instead of sleeping):
 * <ul>
 *   <li>CREATED or ON_ITS_WAY and drop-off deadline passed -> CANCELLED (system); a parcel whose door is open at
 *       the terminal right now is left for the next run, so a drop-off in progress can finish;</li>
 *   <li>READY_FOR_PICKUP and pickup window ended -> EXPIRED (system).</li>
 * </ul>
 * Each parcel is processed in its own transaction so that one failure does not block the others.
 */
@Service
public class DeadlineService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineService.class);

    /**
     * Result of one deadline check.
     *
     * @param cancelled parcels cancelled because the drop-off deadline passed
     * @param expired   parcels expired because the pickup window ended
     */
    public record Report(int cancelled, int expired) {
    }

    /** States of a parcel that has not been dropped off yet. */
    static final List<ParcelState> NOT_DROPPED_OFF = List.of(ParcelState.CREATED, ParcelState.ON_ITS_WAY);

    private final ParcelRepository parcels;
    private final TransitionRecorder transitions;
    private final NotifierPort notifier;
    private final Clock clock;
    private final TransactionTemplate tx;

    /**
     * Creates the service.
     *
     * @param parcels            parcel repository
     * @param transitions        records the system transitions
     * @param notifier           informs sender/recipient
     * @param clock              time source
     * @param transactionManager used to process each parcel in its own transaction
     */
    public DeadlineService(ParcelRepository parcels, TransitionRecorder transitions, NotifierPort notifier,
                           Clock clock, PlatformTransactionManager transactionManager) {
        this.parcels = parcels;
        this.transitions = transitions;
        this.notifier = notifier;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Runs {@link #processDeadlines()} periodically ({@code orbit.rules.deadline-check-interval}). */
    @Scheduled(fixedDelayString = "${orbit.rules.deadline-check-interval:60s}",
            initialDelayString = "${orbit.rules.deadline-check-initial-delay:10s}")
    public void scheduledRun() {
        Report r = processDeadlines();
        if (r.cancelled() + r.expired() > 0) {
            log.info("Deadline check: {} cancelled, {} expired", r.cancelled(), r.expired());
        }
    }

    /**
     * Cancels parcels not dropped off (CREATED or ON_ITS_WAY) whose drop-off deadline passed and expires READY_FOR_PICKUP parcels whose pickup window ended.
     *
     * @return how many parcels were cancelled and expired
     */
    public Report processDeadlines() {
        Instant now = clock.instant();
        List<Long> toCancel = ids(tx.execute(s ->
                parcels.findByStateInAndDropOffDeadlineBefore(NOT_DROPPED_OFF, now)));
        List<Long> toExpire = ids(tx.execute(s ->
                parcels.findByStateAndPickupDeadlineLessThanEqual(ParcelState.READY_FOR_PICKUP, now)));
        int cancelled = 0;
        int expired = 0;
        for (Long id : toCancel) {
            if (Boolean.TRUE.equals(tx.execute(s -> cancelOne(id)))) {
                cancelled++;
            }
        }
        for (Long id : toExpire) {
            if (Boolean.TRUE.equals(tx.execute(s -> expireOne(id)))) {
                expired++;
            }
        }
        return new Report(cancelled, expired);
    }

    private boolean cancelOne(Long id) {
        Parcel p = parcels.findById(id).orElse(null);
        if (p == null || !NOT_DROPPED_OFF.contains(p.getState()) || p.hasOpenTerminalSession(clock.instant())) {
            return false;
        }
        cancelForMissedDropOff(p, transitions, notifier);
        return true;
    }

    /**
     * Cancels a parcel whose drop-off deadline passed (CREATED or ON_ITS_WAY to CANCELLED, by the system), which
     * releases its compartment, and tells the sender. A collected parcel goes back to the sender with the courier.
     *
     * @param p           the parcel
     * @param transitions records the transition
     * @param notifier    informs the sender
     */
    static void cancelForMissedDropOff(Parcel p, TransitionRecorder transitions, NotifierPort notifier) {
        boolean collected = p.getState() == ParcelState.ON_ITS_WAY;
        transitions.apply(p, ParcelEvent.DROP_OFF_DEADLINE_PASSED, Actor.SYSTEM, "Drop-off deadline "
                + p.getDropOffDeadline() + " passed before " + (collected ? "drop-off" : "collection"));
        notifier.send(new Notification(Notification.Type.PARCEL_CANCELLED, p.getTrackingCode(),
                p.getSender().getName(), p.getSender().getEmail(), p.getSender().getPhone(),
                "Reservation " + p.getTrackingCode() + " cancelled",
                "The parcel was not dropped off before the drop-off deadline; the compartment was released."
                        + (collected ? " The courier will return the parcel to you." : "")));
    }

    private boolean expireOne(Long id) {
        Parcel p = parcels.findById(id).orElse(null);
        if (p == null || p.getState() != ParcelState.READY_FOR_PICKUP) {
            return false;
        }
        transitions.apply(p, ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM,
                "Pickup window ended at " + p.getPickupDeadline());
        notifier.send(new Notification(Notification.Type.PARCEL_EXPIRED, p.getTrackingCode(),
                p.getRecipient().getName(), p.getRecipient().getEmail(), p.getRecipient().getPhone(),
                "Parcel " + p.getTrackingCode() + " expired",
                "The pickup window ended. You can still collect it with your code until it is returned."));
        return true;
    }

    private static List<Long> ids(List<Parcel> list) {
        return list == null ? List.of() : list.stream().map(Parcel::getId).toList();
    }
}
