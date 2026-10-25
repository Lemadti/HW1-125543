package pt.ua.deti.tqs.orbit.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Contact;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;

/**
 * Seeds a few parcels through the real services (so every transition is recorded) when the database has none:
 * one CREATED, one ON_ITS_WAY with location scans, one READY_FOR_PICKUP (its pickup code is in the demo inbox).
 */
@Component
@ConditionalOnProperty(prefix = "orbit.demo", name = "seed-parcels", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String CENTRUM = "ORB-WAW-014";

    private final ParcelRepository parcels;
    private final ParcelService parcelService;
    private final TerminalService terminal;

    public DemoDataSeeder(ParcelRepository parcels, ParcelService parcelService, TerminalService terminal) {
        this.parcels = parcels;
        this.parcelService = parcelService;
        this.terminal = terminal;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (parcels.count() > 0) {
            return;
        }
        Contact anna = new Contact("Anna Kowalska", "anna.kowalska@example.com", "+48 501 234 567");
        Contact piotr = new Contact("Piotr Nowak", "piotr.nowak@example.com", "+48 602 789 123");
        Contact ewa = new Contact("Ewa Zielińska", "ewa.zielinska@example.com", "+48 503 111 222");

        RegistrationResult onItsWay = parcelService.register(
                new RegisterParcelCommand(CENTRUM, CompartmentSize.M, "M-07", null, 48, anna, piotr));
        parcelService.collect(onItsWay.deliveryCode(), "Pruszków sorting hub");
        parcelService.addLocation(onItsWay.trackingCode(), "Warsaw Wola depot · Towarowa 22", 52.2300, 20.9850);

        RegistrationResult ready = parcelService.register(
                new RegisterParcelCommand(CENTRUM, CompartmentSize.S, null, null, 24, anna, ewa));
        parcelService.collect(ready.deliveryCode(), "Pruszków sorting hub");
        TerminalResult opened = terminal.startDropOff(CENTRUM, ready.dropOffCode());
        terminal.confirmDropOff(CENTRUM, opened.sessionToken());

        RegistrationResult created = parcelService.register(
                new RegisterParcelCommand("ORB-WAW-021", CompartmentSize.L, null, null, 72, ewa, piotr));

        log.info("Demo parcels: ON_ITS_WAY {} | READY_FOR_PICKUP {} (pickup code in demo inbox) | CREATED {} "
                        + "(delivery code {}, drop-off PIN {})", onItsWay.trackingCode(), ready.trackingCode(),
                created.trackingCode(), created.deliveryCode(), created.dropOffCode());
    }
}
