package pt.ua.deti.tqs.orbit.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.geocoding.NominatimGeocoder;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException.Reason;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * The Nominatim client against a stubbed HTTP server (WireMock): request format, parsing, and the behaviour when the
 * provider is slow, failing, over its limits or returns nothing. The real provider is never called (OSM usage policy).
 *
 * <p>The response bodies are recorded in {@code src/test/resources/nominatim} (see {@code FIXTURES.md}) and loaded with
 * {@link #fixture(String)}: a stub that returns a body the provider would never return is not a test, it is a wish.
 * Unlike a Mockito stub of the HTTP client, WireMock is a real HTTP server, so the request (URL, query, User-Agent),
 * the timeouts and the status codes go through the real client.</p>
 */
@DisplayName("NominatimGeocoder — real HTTP client against a WireMock server")
class NominatimGeocoderIT {

    private WireMockServer server;
    private MutableClock clock;
    private NominatimGeocoder geocoder;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        clock = MutableClock.fixedAt(TestData.T0);
        OrbitProperties.Geocoding cfg = new OrbitProperties.Geocoding("nominatim", server.baseUrl(), "OrbitTest/1.0",
                "pt", Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofHours(24),
                Duration.ofHours(1), 100);
        geocoder = new NominatimGeocoder(cfg, clock);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    /** Reads a recorded response body from {@code src/test/resources/nominatim}. */
    static String fixture(String name) {
        try (InputStream in = NominatimGeocoderIT.class.getClassLoader().getResourceAsStream("nominatim/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no such fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("NFR4: parses the first result and sends a policy-compliant request (User-Agent, limit=1)")
    void parsesFirstResultAndSendsPolicyCompliantRequest() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson(fixture("search-rua-de-coimbra.json"))));

        assertThat(geocoder.geocode("Rua de Coimbra 10, Aveiro")).contains(new GeoPoint(40.6403, -8.6537));
        server.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("Rua de Coimbra 10, Aveiro"))
                .withQueryParam("format", equalTo("jsonv2"))
                .withQueryParam("limit", equalTo("1"))
                .withQueryParam("countrycodes", equalTo("pt"))
                .withHeader("User-Agent", equalTo("OrbitTest/1.0")));
    }

    @Test
    @DisplayName("US1: an empty result means the address was not found")
    void emptyResultMeansNotFound() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson(fixture("search-empty.json"))));
        assertThat(geocoder.geocode("Nowhere")).isEmpty();
    }

    @Test
    @DisplayName("NFR4: a slow provider times out (TIMEOUT)")
    void slowProviderTimesOut() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("[]").withFixedDelay(4000)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    @DisplayName("NFR4: a 503 from the provider is PROVIDER_ERROR")
    void serverErrorIsProviderError() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.PROVIDER_ERROR));
    }

    @Test
    @DisplayName("NFR4: a 429 (usage limit) is RATE_LIMITED")
    void usageLimitIsRateLimited() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(429)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED));
    }

    @Test
    @DisplayName("NFR4: a body that is not a result array is a provider error")
    void malformedBodyIsProviderError() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson(fixture("search-malformed.json"))));
        assertThatThrownBy(() -> geocoder.geocode("x")).isInstanceOf(GeocodingUnavailableException.class);
    }

    @Test
    @DisplayName("NFR4: at most one request per second reaches the provider")
    void enforcesOneRequestPerSecondLocally() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("[]")));
        geocoder.geocode("a");
        assertThatThrownBy(() -> geocoder.geocode("b"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED));
        assertThat(server.getAllServeEvents()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(geocoder.geocode("b")).isEmpty();
    }

    @Test
    @DisplayName("NFR4: an unreachable provider is reported as unavailable")
    void unreachableProviderIsUnavailable() {
        server.stop();
        assertThatThrownBy(() -> geocoder.geocode("x")).isInstanceOf(GeocodingUnavailableException.class);
    }
}
