package pt.ua.deti.tqs.orbit.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.notification.NotificationOutbox;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;
import pt.ua.deti.tqs.orbit.service.DeadlineService;

/**
 * Demo helpers, only present with {@code orbit.demo.enabled=true}: the notification inbox (no real SMS/e-mail),
 * fault injection in the fake hardware, and clock fast-forward to show time-based rules live.
 */
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(prefix = "orbit.demo", name = "enabled", havingValue = "true")
@Tag(name = "Demo", description = "Demo-only helpers (disabled in production)")
public class DemoApiController {

    public record FaultRequest(@NotBlank String stationCode, @NotBlank String compartmentCode,
                               @NotNull FakeLockerHardware.Fault fault) {
    }

    public record AdvanceRequest(@Min(1) @Max(24 * 60) int hours) {
    }

    public record FaultView(String stationCode, String compartmentCode, List<FakeLockerHardware.Fault> faults) {
    }

    private final NotificationOutbox outbox;
    private final FakeLockerHardware hardware;
    private final MutableClock clock;
    private final DeadlineService deadlines;
    private final AttemptGuard guard;

    public DemoApiController(NotificationOutbox outbox, FakeLockerHardware hardware, MutableClock clock,
                             DeadlineService deadlines, AttemptGuard guard) {
        this.outbox = outbox;
        this.hardware = hardware;
        this.clock = clock;
        this.deadlines = deadlines;
        this.guard = guard;
    }

    @GetMapping("/notifications")
    @Operation(summary = "Last notifications sent (the demo inbox)")
    public List<NotificationOutbox.Entry> notifications() {
        return outbox.latest();
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<Void> clearNotifications() {
        outbox.clear();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hardware/faults")
    public List<FaultView> faults() {
        return hardware.activeFaults().entrySet().stream()
                .map(e -> new FaultView(e.getKey().stationCode(), e.getKey().compartmentCode(),
                        e.getValue().stream().sorted().toList()))
                .toList();
    }

    @PostMapping("/hardware/faults")
    @Operation(summary = "Inject a failure in the fake hardware for one compartment")
    public ResponseEntity<Void> injectFault(@Valid @RequestBody FaultRequest request) {
        hardware.inject(new CompartmentRef(request.stationCode(), request.compartmentCode()), request.fault());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/hardware/faults")
    @Operation(summary = "Remove all injected failures (and unblock terminals)")
    public ResponseEntity<Void> clearFaults() {
        hardware.reset();
        guard.resetAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/clock")
    public Map<String, Object> clock() {
        return Map.of("now", clock.instant().toString(), "offsetHours", clock.getOffset().toHours());
    }

    @PostMapping("/clock/advance")
    @Operation(summary = "Move the application clock forward and run the deadline check immediately")
    public Map<String, Object> advance(@Valid @RequestBody AdvanceRequest request) {
        clock.advance(Duration.ofHours(request.hours()));
        DeadlineService.Report report = deadlines.processDeadlines();
        return Map.of("now", clock.instant().toString(), "offsetHours", clock.getOffset().toHours(),
                "cancelled", report.cancelled(), "expired", report.expired());
    }

    @PostMapping("/clock/reset")
    public Map<String, Object> reset() {
        clock.resetOffset();
        return clock();
    }
}
