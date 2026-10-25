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

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Europe/Warsaw"));
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    public static final Pattern TRACKING_CODE = Pattern.compile("^ORB-\\d{6}-\\d{4}$");

    private final SecureRandom random;
    private final SecretKeySpec key;
    private final int pinLength;

    @Autowired
    public CodeService(OrbitProperties properties) {
        this(new SecureRandom(), properties.codes().hmacSecret(), properties.codes().pinLength());
    }

    CodeService(SecureRandom random, String secret, int pinLength) {
        this.random = random;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.pinLength = pinLength;
    }

    public String newPin() {
        StringBuilder sb = new StringBuilder(pinLength);
        for (int i = 0; i < pinLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    public boolean isWellFormedPin(String pin) {
        return pin != null && pin.length() == pinLength && pin.chars().allMatch(Character::isDigit);
    }

    public String hash(String pin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC not available", e);
        }
    }

    public String newTrackingCode(Instant now) {
        return "ORB-" + DAY.format(now) + "-" + String.format("%04d", random.nextInt(10_000));
    }

    public String newDeliveryCode() {
        StringBuilder sb = new StringBuilder("DLV-");
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
