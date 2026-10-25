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

    /** Constructor for JPA. */
    protected Contact() {
    }

    /**
     * Creates a contact.
     *
     * @param name  full name
     * @param email e-mail address
     * @param phone phone number
     */
    public Contact(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the email.
     *
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the phone.
     *
     * @return the phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Name with the last name reduced to an initial, for public pages: "João Santos" becomes "João S.".
     *
     * @return the masked name
     */
    public String maskedName() {
        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
