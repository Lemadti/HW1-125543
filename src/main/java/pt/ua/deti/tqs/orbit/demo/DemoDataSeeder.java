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
 * one CREATED, one ON_ITS_WAY with location scans, and one READY_FOR_PICKUP (its pickup code is in the demo inbox).
 *
 * <p>Active only with {@code orbit.demo.seed-parcels=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "orbit.demo", name = "seed-parcels", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String FORUM = "ORB-AVR-001";
    private static final String STATION = "ORB-AVR-002";

    private final ParcelRepository parcels;
    private final ParcelService parcelService;
    private final TerminalService terminal;

    /**
     * Creates the seeder.
     *
     * @param parcels       used to check whether the database already has parcels
     * @param parcelService registers, collects and locates the demo parcels
     * @param terminal      drops off the parcel that ends up ready for pickup
     */
    public DemoDataSeeder(ParcelRepository parcels, ParcelService parcelService, TerminalService terminal) {
        this.parcels = parcels;
        this.parcelService = parcelService;
        this.terminal = terminal;
    }

    /**
     * Creates the demo parcels if the parcel table is empty; otherwise does nothing.
     *
     * @param args application arguments (unused)
     */
    @Override
    public void run(ApplicationArguments args) {
        if (parcels.count() > 0) {
            return;
        }
        Contact ana = new Contact("Ana Silva", "ana.silva@example.com", "+351 912 345 678");
        Contact joao = new Contact("João Santos", "joao.santos@example.com", "+351 934 567 890");
        Contact maria = new Contact("Maria Costa", "maria.costa@example.com", "+351 961 111 222");

        RegistrationResult onItsWay = parcelService.register(
                new RegisterParcelCommand(FORUM, CompartmentSize.M, "M-07", ana, joao));
        parcelService.collect(onItsWay.deliveryCode(), "Aveiro sorting hub · Taboeira");
        parcelService.addLocation(onItsWay.trackingCode(), "Aveiro depot · Esgueira", 40.6530, -8.6310);

        RegistrationResult ready = parcelService.register(
                new RegisterParcelCommand(FORUM, CompartmentSize.S, null, ana, maria));
        parcelService.collect(ready.deliveryCode(), "Aveiro sorting hub · Taboeira");
        TerminalResult opened = terminal.startDropOff(FORUM, ready.dropOffCode());
        terminal.confirmDropOff(FORUM, opened.sessionToken());

        RegistrationResult created = parcelService.register(
                new RegisterParcelCommand(STATION, CompartmentSize.L, null, maria, joao));

        log.info("Demo parcels: ON_ITS_WAY {} | READY_FOR_PICKUP {} (pickup code in demo inbox) | CREATED {} "
                        + "(delivery code {}, drop-off PIN {})", onItsWay.trackingCode(), ready.trackingCode(),
                created.trackingCode(), created.deliveryCode(), created.dropOffCode());
    }
}
