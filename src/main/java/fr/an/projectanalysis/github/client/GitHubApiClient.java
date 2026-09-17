package fr.an.projectanalysis.github.client;

import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Delegates all HTTP calls to the GitHub REST API.
 * - primary rate limit (403 + X-RateLimit-Remaining: 0) backoff, sleeping until X-RateLimit-Reset
 * - secondary rate limit / 429 / 5xx backoff with retry, honoring Retry-After if present
 * - adds the configured Authorization header, if any
 */
@Component
@Slf4j
public class GitHubApiClient {

    /** last known rate-limiting status, updated after each response, consulted before each request. */
    public record RateLimitStatus(int rateLimitRemaining, Instant rateLimitReset, Instant retryAfter) {
    }

    private static final int RATE_LIMIT_LOW_WATERMARK = 10;

    private final GitHubSyncProperties props;

    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final AtomicReference<RateLimitStatus> rateLimitStatus =
            new AtomicReference<>(new RateLimitStatus(5000, Instant.now().plus(Duration.ofHours(1)), null));

    public GitHubApiClient(GitHubSyncProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** last known rate-limiting status, as observed from the last http response received (or its initial default). */
    public RateLimitStatus getLastRateLimitStatus() {
        return rateLimitStatus.get();
    }

    /** call http GET, return response body as typed object */
    public <T> T callHttpGet(String pathAndQuery, Class<T> type) throws Exception {
        String jsonText = callHttpGet_String(pathAndQuery);
        return mapper.readValue(jsonText, type);
    }

    /** call http GET, return response body (a JSON array) as a typed list */
    public <T> List<T> callHttpGet_List(String pathAndQuery, Class<T> elementClass) throws Exception {
        String jsonText = callHttpGet_String(pathAndQuery);
        val type = listTypeOf(elementClass);
        return mapper.readValue(jsonText, type);
    }

    private <T> CollectionType listTypeOf(Class<T> elementClass) {
        return mapper.getTypeFactory().constructCollectionType(List.class, elementClass);
    }

//    /** call http GET, return response body as JsonNode */
//    public JsonNode callHttpGet_JsonNode(String pathAndQuery) throws Exception {
//        String respText = callHttpGet_String(pathAndQuery);
//        return mapper.readTree(respText);
//    }

    /** GETs the given path+query (relative to the configured GitHub API base URL) and parses the JSON response. */
    public String callHttpGet_String(String pathAndQuery) throws Exception {
        for (int attempt = 1; ; attempt++) {
            awaitRateLimitBudget();

            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.getApiBaseUrl() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .GET();
            String httpHeaderAuth = props.getHttpHeaderAuth();
            if (httpHeaderAuth != null && !httpHeaderAuth.isBlank()) {
                rb.header("Authorization", httpHeaderAuth);
            }

            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            updateRateLimitStatus(resp);
            int sc = resp.statusCode();
            if (sc == 200) {
                return resp.body();
            }

            if (sc == 403 && isPrimaryRateLimitExhausted(resp) && attempt <= 5) {
                long backoff = primaryRateLimitResetMs(resp);
                log.warn("HTTP {} on {}, GitHub primary rate limit exhausted, retry {} in {}ms", sc, pathAndQuery, attempt, backoff);
                sleep(backoff);
                continue;
            }
            if ((sc == 403 || sc == 429 || sc >= 500) && attempt <= 5) {
                long backoff = retryAfterMs(resp, attempt);
                log.warn("HTTP {} on {}, retry {} in {}ms", sc, pathAndQuery, attempt, backoff);
                sleep(backoff);
                continue;
            }
            throw new RuntimeException("HTTP " + sc + " on " + pathAndQuery + " : "
                    + resp.body().substring(0, Math.min(500, resp.body().length())));
        }
    }

    /** sleeps before an http call when the last known rate-limit status requires it, then resets it once its window has elapsed. */
    private void awaitRateLimitBudget() {
        RateLimitStatus status = rateLimitStatus.get();
        Instant now = Instant.now();

        if (status.retryAfter() != null && now.isBefore(status.retryAfter())) {
            log.warn("Rate limit retry-after in effect, sleeping until {}", status.retryAfter());
            sleepUntil(status.retryAfter());
            now = Instant.now();
        }

        if (!now.isBefore(status.rateLimitReset())) {
            rateLimitStatus.compareAndSet(status, new RateLimitStatus(5000, status.rateLimitReset(), null));
            return;
        }

        if (status.rateLimitRemaining() <= RATE_LIMIT_LOW_WATERMARK) {
            log.warn("Rate limit remaining {} <= {}, sleeping until reset {}",
                    status.rateLimitRemaining(), RATE_LIMIT_LOW_WATERMARK, status.rateLimitReset());
            sleepUntil(status.rateLimitReset());
            rateLimitStatus.compareAndSet(status, new RateLimitStatus(5000, status.rateLimitReset(), null));
        }
    }

    private void updateRateLimitStatus(HttpResponse<?> resp) {
        rateLimitStatus.updateAndGet(prev -> new RateLimitStatus(
                resp.headers().firstValue("X-RateLimit-Remaining")
                        .map(s -> Integer.parseInt(s.trim())).orElse(prev.rateLimitRemaining()),
                resp.headers().firstValue("X-RateLimit-Reset")
                        .map(s -> Instant.ofEpochSecond(Long.parseLong(s.trim()))).orElse(prev.rateLimitReset()),
                resp.headers().firstValue("Retry-After")
                        .map(s -> Instant.now().plusSeconds(Long.parseLong(s.trim()))).orElse(null)));
    }

    private static boolean isPrimaryRateLimitExhausted(HttpResponse<?> resp) {
        return resp.headers().firstValue("X-RateLimit-Remaining")
                .map(s -> "0".equals(s.trim()))
                .orElse(false);
    }

    private static long primaryRateLimitResetMs(HttpResponse<?> resp) {
        return resp.headers().firstValue("X-RateLimit-Reset")
                .map(s -> Math.max(0, Instant.ofEpochSecond(Long.parseLong(s.trim())).toEpochMilli() - System.currentTimeMillis()) + 1000)
                .orElse(60_000L);
    }

    private static long retryAfterMs(HttpResponse<?> resp, int attempt) {
        return resp.headers().firstValue("Retry-After")
                .map(s -> Long.parseLong(s.trim()) * 1000L)
                .orElse((long) Math.min(60_000, 1000L * (1L << attempt))); // 2s,4s,8s,...
    }

    private static void sleepUntil(Instant instant) {
        sleep(Math.max(0, instant.toEpochMilli() - System.currentTimeMillis()) + 1000);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

}
