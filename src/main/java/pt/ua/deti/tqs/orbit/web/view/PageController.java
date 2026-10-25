package pt.ua.deti.tqs.orbit.web.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.service.StationService;

/**
 * Thymeleaf pages. Pages are rendered server-side (layout, navigation, static content) and load live data from the
 * REST API with small vanilla JS modules in /static/js.
 */
@Controller
public class PageController {

    private final OrbitProperties properties;
    private final StationService stations;

    /**
     * Creates the controller.
     *
     * @param properties business rules (demo flag, assistance phone)
     * @param stations   station queries (validates station codes in URLs)
     */
    public PageController(OrbitProperties properties, StationService stations) {
        this.properties = properties;
        this.stations = stations;
    }

    /**
     * Attributes shared by every page (demo flag, assistance phone).
     *
     * @param model the view model
     */
    @ModelAttribute
    void common(Model model) {
        model.addAttribute("demoEnabled", properties.demo().enabled());
        model.addAttribute("assistancePhone", properties.assistancePhone());
    }

    /**
     * Find stations page (UC1).
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/")
    String findStations(Model model) {
        model.addAttribute("nav", "find");
        return "find";
    }

    /**
     * Register, step 1: parcel size and station.
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/register")
    String registerStep1(Model model) {
        model.addAttribute("nav", "register");
        return "register-station";
    }

    /**
     * Register, step 2: choose a compartment in the station front view.
     *
     * @param stationCode station code (404 page if unknown)
     * @param size        parcel size
     * @param address     the address searched in step 1, kept in the search box
     * @param model       the view model
     * @return the view name
     */
    @GetMapping("/register/{stationCode}")
    String registerStep2(@PathVariable String stationCode, @RequestParam(defaultValue = "M") String size,
                         @RequestParam(required = false) String address, Model model) {
        stations.detail(stationCode); // 404 if unknown
        model.addAttribute("nav", "register");
        model.addAttribute("stationCode", stationCode);
        model.addAttribute("size", size);
        model.addAttribute("address", address);
        return "register-compartment";
    }

    /**
     * Register, step 3: lease and contact details, then the confirmation.
     *
     * @param stationCode     station code
     * @param compartmentCode chosen compartment
     * @param size            parcel size
     * @param model           the view model
     * @return the view name
     */
    @GetMapping("/register/{stationCode}/{compartmentCode}")
    String registerStep3(@PathVariable String stationCode, @PathVariable String compartmentCode,
                         @RequestParam(defaultValue = "M") String size, Model model) {
        stations.detail(stationCode);
        model.addAttribute("nav", "register");
        model.addAttribute("stationCode", stationCode);
        model.addAttribute("compartmentCode", compartmentCode);
        model.addAttribute("size", size);
        return "register-details";
    }

    /**
     * Tracking search page (UC5).
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/tracking")
    String tracking(Model model) {
        model.addAttribute("nav", "tracking");
        return "tracking";
    }

    /**
     * Tracking detail page.
     *
     * @param trackingCode tracking code
     * @param model        the view model
     * @return the view name
     */
    @GetMapping("/tracking/{trackingCode}")
    String trackingDetail(@PathVariable String trackingCode, Model model) {
        model.addAttribute("nav", "tracking");
        model.addAttribute("trackingCode", trackingCode);
        return "tracking-detail";
    }

    /**
     * Operator workspace (UC6).
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/operator")
    String operator(Model model) {
        model.addAttribute("nav", "operator");
        return "operator";
    }

    /**
     * Station supervision page.
     *
     * @param stationCode station code
     * @param model       the view model
     * @return the view name
     */
    @GetMapping("/operator/stations/{stationCode}")
    String operatorStation(@PathVariable String stationCode, Model model) {
        stations.detail(stationCode);
        model.addAttribute("nav", "operator");
        model.addAttribute("stationCode", stationCode);
        return "operator-station";
    }

    /**
     * Operator parcel detail with the lifecycle diagram.
     *
     * @param trackingCode tracking code
     * @param model        the view model
     * @return the view name
     */
    @GetMapping("/operator/parcels/{trackingCode}")
    String operatorParcel(@PathVariable String trackingCode, Model model) {
        model.addAttribute("nav", "operator");
        model.addAttribute("trackingCode", trackingCode);
        return "operator-parcel";
    }

    /**
     * Locker terminal simulator: choose a station.
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/terminal")
    String terminalChooser(Model model) {
        model.addAttribute("nav", "terminal");
        model.addAttribute("stations", stations.listAll(null));
        return "terminal-select";
    }

    /**
     * Locker terminal kiosk of one station (UC3 and UC4).
     *
     * @param stationCode station code
     * @param model       the view model
     * @return the view name
     */
    @GetMapping("/terminal/{stationCode}")
    String terminal(@PathVariable String stationCode, Model model) {
        model.addAttribute("station", stations.detail(stationCode).station());
        return "terminal";
    }

    /**
     * Courier page: collect (scan delivery code) and report location.
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/courier")
    String courier(Model model) {
        model.addAttribute("nav", "courier");
        return "courier";
    }

    /**
     * Demo tools page; redirects home when demo mode is off.
     *
     * @param model the view model
     * @return the view name
     */
    @GetMapping("/demo")
    String demo(Model model) {
        if (!properties.demo().enabled()) {
            return "redirect:/";
        }
        model.addAttribute("nav", "demo");
        model.addAttribute("stations", stations.listAll(null));
        return "demo";
    }
}
