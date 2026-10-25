package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * "Two partners must never get the same last compartment": many concurrent HTTP registrations against the real
 * server and database; exactly as many succeed as there are free compartments, the rest get 409 STATION_FULL.
 */
class ConcurrentAllocationIT extends AbstractIntegrationTest {

    private static final int REQUESTS = 24;

    private List<Integer> fireConcurrently(String body) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/parcels"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                    return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : futures) {
                codes.add(f.get());
            }
            return codes;
        }
    }

    @Test
    void lastCompartmentGoesToExactlyOnePartner() throws Exception {
        register(CENTRUM, "M", "M-03", 24);
        register(CENTRUM, "M", "M-07", 24);
        register(CENTRUM, "M", "M-11", 24); // only M-15 left

        Map<Integer, Long> byStatus = fireConcurrently(registerBody(CENTRUM, "M", null, 24)).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, (long) REQUESTS - 1);
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM parcel p JOIN compartment c ON c.id = p.compartment_id
                WHERE c.code = 'M-15' AND p.state IN ('CREATED','ON_ITS_WAY','READY_FOR_PICKUP')""", Integer.class);
        assertThat(active).isEqualTo(1);
    }

    @Test
    void concurrentRequestsForTheSameChosenCompartment() throws Exception {
        Map<Integer, Long> byStatus = fireConcurrently(registerBody(CENTRUM, "L", "L-16", 24)).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, (long) REQUESTS - 1);
    }

    @Test
    void freeCompartmentsAreSharedWithoutDuplicates() throws Exception {
        // Small: 7 free at Centrum (S-06 out of service) -> 7 winners, all different compartments
        Map<Integer, Long> byStatus = fireConcurrently(registerBody(CENTRUM, "S", null, 24)).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 7L).containsEntry(409, (long) REQUESTS - 7);
        Integer distinct = jdbc.queryForObject("SELECT count(DISTINCT compartment_id) FROM parcel", Integer.class);
        assertThat(distinct).isEqualTo(7);
    }
}
