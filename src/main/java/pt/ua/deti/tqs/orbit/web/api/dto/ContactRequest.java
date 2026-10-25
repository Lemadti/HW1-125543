package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.Contact;

public record ContactRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone) {

    public Contact toContact() {
        return new Contact(name.trim(), email.trim(), phone.trim());
    }
}
