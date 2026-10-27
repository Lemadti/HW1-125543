package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * "Two partners must never get the same last compartment": many concurrent HTTP registrations against the real
 * server and database; exactly as many succeed as there are free compartments, the rest get 409 STATION_FULL.
 *
 * <p>This needs real HTTP ({@code TestRestTemplate}, one request per thread, released together by a latch): MockMvc
 * would not exercise the servlet container's request threads, the connection pool and the database locks the way
 * concurrent partners do.</p>
 */
@DisplayName("Concurrent allocation — real server, 24 simultaneous registrations, real PostgreSQL")
class ConcurrentAllocationIT extends AbstractIntegrationTest {

    private static final int REQUESTS = 24;

    private List<Integer> fireConcurrently(String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return restClient.postForEntity("/api/v1/parcels", request, String.class).getStatusCode().value();
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
    @DisplayName("Compartment allocation: the last free compartment goes to exactly one of 24 partners")
    void lastCompartmentGoesToExactlyOnePartner() throws Exception {
        register(FORUM, "M", "M-03");
        register(FORUM, "M", "M-07");
        register(FORUM, "M", "M-11"); // only M-15 left

        Map<Integer, Long> byStatus = fireConcurrently(registerBody(FORUM, "M", null)).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, (long) REQUESTS - 1);
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM parcel p JOIN compartment c ON c.id = p.compartment_id
                WHERE c.code = 'M-15' AND p.state IN ('CREATED','ON_ITS_WAY','READY_FOR_PICKUP')""", Integer.class);
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("Compartment allocation: 24 partners choosing the same compartment, exactly one wins")
    void concurrentRequestsForTheSameChosenCompartment() throws Exception {
        Map<Integer, Long> byStatus = fireConcurrently(registerBody(FORUM, "L", "L-16")).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, (long) REQUESTS - 1);
    }

    @Test
    @DisplayName("Compartment allocation: 7 free compartments go to 7 partners, never twice")
    void freeCompartmentsAreSharedWithoutDuplicates() throws Exception {
        // Small: 7 free at Fórum Aveiro (S-06 out of service) -> 7 winners, all different compartments
        Map<Integer, Long> byStatus = fireConcurrently(registerBody(FORUM, "S", null)).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 7L).containsEntry(409, (long) REQUESTS - 7);
        Integer distinct = jdbc.queryForObject("SELECT count(DISTINCT compartment_id) FROM parcel", Integer.class);
        assertThat(distinct).isEqualTo(7);
    }
}
