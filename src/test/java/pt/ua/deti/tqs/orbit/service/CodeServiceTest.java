package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CodeServiceTest {

    private final CodeService codes = new CodeService(new SecureRandom(), "test-secret", 6);

    @RepeatedTest(20)
    void pinHasSixDigits() {
        assertThat(codes.newPin()).matches("\\d{6}");
    }

    @Test
    void pinsAreSpreadOverTheWholeSpace() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(codes.newPin());
        }
        // 2000 draws out of 10^6: collisions are rare (birthday bound ~2), so almost all distinct.
        assertThat(seen.size()).isGreaterThan(1990);
    }

    @Test
    void hashIsDeterministicKeyedAndNotThePin() {
        CodeService other = new CodeService(new SecureRandom(), "another-secret", 6);
        assertThat(codes.hash("123456")).isEqualTo(codes.hash("123456"))
                .isNotEqualTo(codes.hash("123457"))
                .isNotEqualTo(other.hash("123456"))
                .doesNotContain("123456")
                .hasSize(64);
    }

    @Test
    void validatesPinFormat() {
        assertThat(codes.isWellFormedPin("012345")).isTrue();
        assertThat(codes.isWellFormedPin("12345")).isFalse();
        assertThat(codes.isWellFormedPin("1234567")).isFalse();
        assertThat(codes.isWellFormedPin("12a456")).isFalse();
        assertThat(codes.isWellFormedPin(null)).isFalse();
    }

    @Test
    void trackingCodeUsesWarsawDate() {
        // 23:30 UTC on 24 Oct is already 25 Oct in Warsaw (UTC+2 in summer time)
        String code = codes.newTrackingCode(Instant.parse("2026-10-24T23:30:00Z"));
        assertThat(code).matches(CodeService.TRACKING_CODE.pattern()).startsWith("ORB-261025-");
    }

    @Test
    void deliveryCodeFormat() {
        assertThat(codes.newDeliveryCode()).matches("DLV-[A-HJ-NP-Z2-9]{12}");
    }
}
