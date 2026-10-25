package pt.ua.deti.tqs.orbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Contact details of a sender or recipient. */
@Embeddable
public class Contact {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    protected Contact() {
    }

    public Contact(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    /** "Piotr Nowak" -> "Piotr N." (used on public pages). */
    public String maskedName() {
        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
