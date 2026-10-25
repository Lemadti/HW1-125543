package pt.ua.deti.tqs.orbit.web.view;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import pt.ua.deti.tqs.orbit.domain.OrbitException;

/** Friendly error page for the Thymeleaf pages. */
@ControllerAdvice(assignableTypes = PageController.class)
public class PageExceptionHandler {

    /** Creates the handler. */
    public PageExceptionHandler() {
        // no state
    }

    /**
     * Shows the error page for unknown stations or parcels in page URLs.
     *
     * @param e     the exception
     * @param model the view model
     * @return the error view
     */
    @ExceptionHandler(OrbitException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(OrbitException e, Model model) {
        model.addAttribute("message", e.getMessage());
        model.addAttribute("demoEnabled", false);
        model.addAttribute("assistancePhone", "+351 234 100 200");
        return "error-page";
    }
}
