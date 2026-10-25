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

    public PageController(OrbitProperties properties, StationService stations) {
        this.properties = properties;
        this.stations = stations;
    }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("demoEnabled", properties.demo().enabled());
        model.addAttribute("assistancePhone", properties.assistancePhone());
    }

    @GetMapping("/")
    String findStations(Model model) {
        model.addAttribute("nav", "find");
        return "find";
    }

    @GetMapping("/register")
    String registerStep1(Model model) {
        model.addAttribute("nav", "register");
        return "register-station";
    }

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

    @GetMapping("/tracking")
    String tracking(Model model) {
        model.addAttribute("nav", "tracking");
        return "tracking";
    }

    @GetMapping("/tracking/{trackingCode}")
    String trackingDetail(@PathVariable String trackingCode, Model model) {
        model.addAttribute("nav", "tracking");
        model.addAttribute("trackingCode", trackingCode);
        return "tracking-detail";
    }

    @GetMapping("/operator")
    String operator(Model model) {
        model.addAttribute("nav", "operator");
        return "operator";
    }

    @GetMapping("/operator/stations/{stationCode}")
    String operatorStation(@PathVariable String stationCode, Model model) {
        stations.detail(stationCode);
        model.addAttribute("nav", "operator");
        model.addAttribute("stationCode", stationCode);
        return "operator-station";
    }

    @GetMapping("/operator/parcels/{trackingCode}")
    String operatorParcel(@PathVariable String trackingCode, Model model) {
        model.addAttribute("nav", "operator");
        model.addAttribute("trackingCode", trackingCode);
        return "operator-parcel";
    }

    @GetMapping("/terminal")
    String terminalChooser(Model model) {
        model.addAttribute("nav", "terminal");
        model.addAttribute("stations", stations.listAll(null));
        return "terminal-select";
    }

    @GetMapping("/terminal/{stationCode}")
    String terminal(@PathVariable String stationCode, Model model) {
        model.addAttribute("station", stations.detail(stationCode).station());
        return "terminal";
    }

    @GetMapping("/courier")
    String courier(Model model) {
        model.addAttribute("nav", "courier");
        return "courier";
    }

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
