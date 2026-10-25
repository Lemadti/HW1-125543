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
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * provider is slow, failing, over its limits or returns nothing. The real provider is never called.
 */
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
                "pl", Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofHours(24),
                Duration.ofHours(1), 100);
        geocoder = new NominatimGeocoder(cfg, clock);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void parsesFirstResultAndSendsPolicyCompliantRequest() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson(
                "[{\"lat\":\"52.2322861\",\"lon\":\"21.0129197\",\"display_name\":\"Chmielna 30, Warszawa\"}]")));

        assertThat(geocoder.geocode("Chmielna 30, Warsaw")).contains(new GeoPoint(52.2322861, 21.0129197));
        server.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("Chmielna 30, Warsaw"))
                .withQueryParam("format", equalTo("jsonv2"))
                .withQueryParam("limit", equalTo("1"))
                .withQueryParam("countrycodes", equalTo("pl"))
                .withHeader("User-Agent", equalTo("OrbitTest/1.0")));
    }

    @Test
    void emptyResultMeansNotFound() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("[]")));
        assertThat(geocoder.geocode("Nowhere")).isEmpty();
    }

    @Test
    void slowProviderTimesOut() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("[]").withFixedDelay(4000)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void serverErrorIsProviderError() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.PROVIDER_ERROR));
    }

    @Test
    void usageLimitIsRateLimited() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(429)));
        assertThatThrownBy(() -> geocoder.geocode("x"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED));
    }

    @Test
    void malformedBodyIsProviderError() {
        server.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("{\"oops\":true}")));
        assertThatThrownBy(() -> geocoder.geocode("x")).isInstanceOf(GeocodingUnavailableException.class);
    }

    @Test
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
    void unreachableProviderIsUnavailable() {
        server.stop();
        assertThatThrownBy(() -> geocoder.geocode("x")).isInstanceOf(GeocodingUnavailableException.class);
    }
}
