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
 *   <li>CREATED and lease ended (drop-off deadline) -> CANCELLED (system);</li>
 *   <li>READY_FOR_PICKUP and pickup window ended -> EXPIRED (system).</li>
 * </ul>
 * Each parcel is processed in its own transaction so that one failure does not block the others.
 */
@Service
public class DeadlineService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineService.class);

    public record Report(int cancelled, int expired) {
    }

    private final ParcelRepository parcels;
    private final TransitionRecorder transitions;
    private final NotifierPort notifier;
    private final Clock clock;
    private final TransactionTemplate tx;

    public DeadlineService(ParcelRepository parcels, TransitionRecorder transitions, NotifierPort notifier,
                           Clock clock, PlatformTransactionManager transactionManager) {
        this.parcels = parcels;
        this.transitions = transitions;
        this.notifier = notifier;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${orbit.rules.deadline-check-interval:60s}",
            initialDelayString = "${orbit.rules.deadline-check-initial-delay:10s}")
    public void scheduledRun() {
        Report r = processDeadlines();
        if (r.cancelled() + r.expired() > 0) {
            log.info("Deadline check: {} cancelled, {} expired", r.cancelled(), r.expired());
        }
    }

    public Report processDeadlines() {
        Instant now = clock.instant();
        List<Long> toCancel = ids(tx.execute(s ->
                parcels.findByStateAndDropOffDeadlineLessThanEqual(ParcelState.CREATED, now)));
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
        if (p == null || p.getState() != ParcelState.CREATED) {
            return false;
        }
        transitions.apply(p, ParcelEvent.DROP_OFF_DEADLINE_PASSED, Actor.SYSTEM,
                "Lease ended at " + p.getDropOffDeadline() + " before drop-off");
        notifier.send(new Notification(Notification.Type.PARCEL_CANCELLED, p.getTrackingCode(),
                p.getSender().getName(), p.getSender().getEmail(), p.getSender().getPhone(),
                "Reservation " + p.getTrackingCode() + " cancelled",
                "The parcel was not collected before the end of the lease; the compartment was released."));
        return true;
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
