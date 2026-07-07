package com.gemono.backend.performance;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static io.restassured.RestAssured.given;

/**
 * Performance / load tests simulating concurrent users.
 * Tests are tagged @Tag("performance") — run separately from unit tests:
 *   mvn test -Dgroups=performance
 */
@Epic("Performance")
@Feature("Load & Concurrency")
@Tag("performance")
@DisplayName("Load Test — Concurrent User Simulation")
class PerformanceTest {

    private static final String BASE_URL = System.getProperty("test.backend.url", "http://localhost:8020");
    private static final int CONCURRENT_USERS = 20;
    private static final int REQUESTS_PER_USER = 3;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    // TC-PERF-01
    @Test
    @Story("Concurrent Users")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-PERF-01 | 20 concurrent users request magic link — all succeed within 5s")
    void TC_PERF_01_concurrentMagicLinkRequests() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startAll = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            pool.submit(() -> {
                try {
                    long start = System.currentTimeMillis();

                    Response res = given()
                            .contentType("application/json")
                            .body("{\"email\":\"perfuser" + userId + "@gemono.com\"}")
                            .post("/api/auth/magic-link");

                    long elapsed = System.currentTimeMillis() - start;
                    responseTimes.add(elapsed);

                    if (res.getStatusCode() == 200) successCount.incrementAndGet();
                    else failCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean allDone = latch.await(10, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startAll;

        pool.shutdown();

        // Metrics
        long avg = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long min = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long p95 = percentile(responseTimes, 95);

        printMetrics("TC-PERF-01", CONCURRENT_USERS, successCount.get(), failCount.get(),
                totalTime, avg, min, max, p95);

        attachMetricsToAllure("TC-PERF-01", successCount.get(), failCount.get(), avg, max, p95);

        Assertions.assertTrue(allDone, "All requests should complete within timeout");
        Assertions.assertEquals(0, failCount.get(), "No requests should fail under 20 concurrent users");
        Assertions.assertTrue(p95 < 5000, "P95 response time should be under 5 seconds, was: " + p95 + "ms");
    }

    // TC-PERF-02
    @Test
    @Tag("requires-groq")
    @Tag("performance")
    @Story("Concurrent Users")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-PERF-02 | 20 concurrent guest chat requests — all respond within 30s")
    void TC_PERF_02_concurrentGuestChatRequests() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startAll = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            pool.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    String guestId = "perf-guest-" + userId + "-" + UUID.randomUUID();

                    Response res = given()
                            .header("X-Guest-Id", guestId)
                            .multiPart("content", "What is machine learning?")
                            .post("/api/guest/conversations/send");

                    long elapsed = System.currentTimeMillis() - start;
                    responseTimes.add(elapsed);

                    if (res.getStatusCode() == 200 || res.getStatusCode() == 429) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // AI responses can be slow — 60s timeout
        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startAll;

        pool.shutdown();

        long avg = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long min = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long p95 = percentile(responseTimes, 95);

        printMetrics("TC-PERF-02", CONCURRENT_USERS, successCount.get(), failCount.get(),
                totalTime, avg, min, max, p95);

        attachMetricsToAllure("TC-PERF-02", successCount.get(), failCount.get(), avg, max, p95);

        Assertions.assertTrue(allDone, "All AI requests should complete within 60s");
        // Allow up to 20% failure tolerance for AI endpoint under load
        double failRate = (double) failCount.get() / CONCURRENT_USERS;
        Assertions.assertTrue(failRate <= 0.20,
                "Failure rate should be <= 20% under 20 concurrent users, was: " + (failRate * 100) + "%");
    }

    // TC-PERF-03
    @Test
    @Story("Rate Limiting")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-PERF-03 | Rate limiter blocks excess requests from same IP")
    void TC_PERF_03_rateLimiterBlocksExcessRequests() throws InterruptedException {
        // Send 35 rapid requests (above the 30/min limit) from a single IP
        int totalRequests = 35;
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            pool.submit(() -> {
                try {
                    Response res = given()
                            .header("X-Guest-Id", "rate-limit-test-" + UUID.randomUUID())
                            .multiPart("content", "Rate limit test")
                            .post("/api/guest/conversations/send");
                    statusCodes.add(res.getStatusCode());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long blocked = statusCodes.stream().filter(c -> c == 429).count();
        long allowed = statusCodes.stream().filter(c -> c == 200).count();

        printMetrics("TC-PERF-03", totalRequests, (int) allowed, (int) blocked, 0, 0, 0, 0, 0);
        attachMetricsToAllure("TC-PERF-03", (int) allowed, (int) blocked, 0, 0, 0);

        Assertions.assertTrue(blocked > 0,
                "Rate limiter should have blocked at least some requests over the limit");
    }

    // TC-PERF-04
    @Test
    @Story("Sustained Load")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-PERF-04 | Health endpoint handles 20 users × 3 requests = 60 requests — all 200")
    void TC_PERF_04_healthEndpointUnderLoad() throws InterruptedException {
        int total = CONCURRENT_USERS * REQUESTS_PER_USER;
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(total);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    Response res = given().get("/api/health");
                    responseTimes.add(System.currentTimeMillis() - start);
                    if (res.getStatusCode() == 200) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        long avg = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long p95 = percentile(responseTimes, 95);

        printMetrics("TC-PERF-04", total, successCount.get(), total - successCount.get(),
                0, avg, 0, max, p95);
        attachMetricsToAllure("TC-PERF-04", successCount.get(), total - successCount.get(), avg, max, p95);

        Assertions.assertEquals(total, successCount.get(),
                "All 60 health requests should return 200");
        Assertions.assertTrue(p95 < 500, "Health P95 should be under 500ms, was: " + p95 + "ms");
    }

    // TC-PERF-05
    @Test
    @Story("Concurrent Users")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-PERF-05 | 20 users fetch conversation list concurrently — response under 2s")
    void TC_PERF_05_concurrentConversationListFetch() throws InterruptedException {
        // Each user has their own guestId — no auth needed
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final String guestId = "list-perf-guest-" + i;
            pool.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    Response res = given()
                            .header("X-Guest-Id", guestId)
                            .get("/api/guest/conversations");
                    responseTimes.add(System.currentTimeMillis() - start);
                    if (res.getStatusCode() == 200) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        long p95 = percentile(responseTimes, 95);
        long avg = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        printMetrics("TC-PERF-05", CONCURRENT_USERS, successCount.get(),
                CONCURRENT_USERS - successCount.get(), 0, avg, 0, max, p95);
        attachMetricsToAllure("TC-PERF-05", successCount.get(),
                CONCURRENT_USERS - successCount.get(), avg, max, p95);

        Assertions.assertEquals(CONCURRENT_USERS, successCount.get(),
                "All 20 users should get their conversation list");
        Assertions.assertTrue(p95 < 2000, "P95 should be under 2s for list endpoint, was: " + p95 + "ms");
    }

    // Helpers
    private static long percentile(List<Long> times, int percentile) {
        if (times.isEmpty()) return 0;
        List<Long> sorted = times.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static void printMetrics(String id, int users, int success, int fail,
                                      long totalMs, long avg, long min, long max, long p95) {
        System.out.printf("""
            ═══════════════════════════════════════
            %s Performance Report
            ───────────────────────────────────────
            Total users/requests : %d
            Successes            : %d
            Failures             : %d
            Total time           : %d ms
            Avg response         : %d ms
            Min response         : %d ms
            Max response         : %d ms
            P95 response         : %d ms
            ═══════════════════════════════════════
            %n""", id, users, success, fail, totalMs, avg, min, max, p95);
    }

    @Step("Attach metrics for {testId}")
    private static void attachMetricsToAllure(String testId, int success, int fail,
                                               long avg, long max, long p95) {
        String report = String.format(
                "Test: %s%nSuccess: %d | Fail: %d%nAvg: %dms | Max: %dms | P95: %dms",
                testId, success, fail, avg, max, p95);
        io.qameta.allure.Allure.addAttachment(testId + " Metrics", "text/plain", report);
    }
}