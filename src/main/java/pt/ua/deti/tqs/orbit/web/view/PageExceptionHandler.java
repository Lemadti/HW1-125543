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

    @ExceptionHandler(OrbitException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(OrbitException e, Model model) {
        model.addAttribute("message", e.getMessage());
        model.addAttribute("demoEnabled", false);
        model.addAttribute("assistancePhone", "+48 22 100 20 30");
        return "error-page";
    }
}
