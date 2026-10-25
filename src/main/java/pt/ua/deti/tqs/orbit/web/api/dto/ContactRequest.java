package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.Contact;

/**
 * Contact details in a request.
 *
 * @param name  full name (required, up to 120 characters)
 * @param email e-mail address (required, valid format)
 * @param phone phone number (required, 7 to 20 digits, spaces or symbols)
 */
public record ContactRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone) {

    /**
     * Converts to the domain value, trimming every field.
     *
     * @return the contact
     */
    public Contact toContact() {
        return new Contact(name.trim(), email.trim(), phone.trim());
    }
}
