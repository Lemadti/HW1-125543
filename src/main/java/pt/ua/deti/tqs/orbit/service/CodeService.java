package pt.ua.deti.tqs.orbit.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;

/**
 * Generates and protects codes.
 * <ul>
 *   <li>Tracking code (public): {@code ORB-yyMMdd-NNNN}.</li>
 *   <li>Delivery code (QR on the box): {@code DLV-} + 12 random characters.</li>
 *   <li>Drop-off / pickup PIN (secret): 6 digits from {@link SecureRandom}; only an HMAC-SHA256 is stored, so the
 *       database never holds the PIN, and lookup at the terminal stays a single indexed query.</li>
 * </ul>
 */
@Service
public class CodeService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Europe/Lisbon"));
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    /** Format of a tracking code. */
    public static final Pattern TRACKING_CODE = Pattern.compile("^ORB-\\d{6}-\\d{4}$");

    private final SecureRandom random;
    private final SecretKeySpec key;
    private final int pinLength;

    /**
     * Creates the service with a {@link SecureRandom}, the HMAC secret and the PIN length from configuration.
     *
     * @param properties business rules ({@code orbit.codes.*})
     */
    @Autowired
    public CodeService(OrbitProperties properties) {
        this(new SecureRandom(), properties.codes().hmacSecret(), properties.codes().pinLength());
    }

    /**
     * Creates the service with explicit parameters (tests).
     *
     * @param random    random source
     * @param secret    HMAC key
     * @param pinLength number of digits in a PIN
     */
    CodeService(SecureRandom random, String secret, int pinLength) {
        this.random = random;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.pinLength = pinLength;
    }

    /**
     * Generates a random numeric PIN.
     *
     * @return the PIN, e.g. "042917"
     */
    public String newPin() {
        StringBuilder sb = new StringBuilder(pinLength);
        for (int i = 0; i < pinLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Whether the value has the expected number of digits and nothing else.
     *
     * @param pin value typed at the terminal
     * @return true if well formed
     */
    public boolean isWellFormedPin(String pin) {
        return pin != null && pin.length() == pinLength && pin.chars().allMatch(Character::isDigit);
    }

    /**
     * HMAC-SHA256 of the PIN, hex encoded. Deterministic, so it can be looked up in the database.
     *
     * @param pin the PIN
     * @return 64 hex characters
     */
    public String hash(String pin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC not available", e);
        }
    }

    /**
     * Generates a tracking code {@code ORB-yyMMdd-NNNN} using the Lisbon date.
     *
     * @param now current time
     * @return the tracking code (uniqueness is checked by the caller)
     */
    public String newTrackingCode(Instant now) {
        return "ORB-" + DAY.format(now) + "-" + String.format("%04d", random.nextInt(10_000));
    }

    /**
     * Generates the QR content for the label: {@code DLV-} plus 12 unambiguous characters.
     *
     * @return the delivery code (uniqueness is checked by the caller)
     */
    public String newDeliveryCode() {
        StringBuilder sb = new StringBuilder("DLV-");
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
