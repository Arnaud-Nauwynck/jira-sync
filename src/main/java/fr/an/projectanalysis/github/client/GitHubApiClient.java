package fr.an.projectanalysis.github.client;

import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Delegates all HTTP calls to the GitHub REST API.
 * - primary rate limit (403 + X-RateLimit-Remaining: 0) backoff, sleeping until X-RateLimit-Reset
 * - secondary rate limit / 429 / 5xx backoff with retry, honoring Retry-After if present
 * - adds the configured Authorization header, if any
 */
@Component
@Slf4j
public class GitHubApiClient {

    private final GitHubSyncProperties props;

    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GitHubApiClient(GitHubSyncProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** GETs the given path+query (relative to the configured GitHub API base URL) and parses the JSON response into {@code type}. */
    public <T> T callHttpGet(String pathAndQuery, Class<T> type) throws Exception {
        return mapper.treeToValue(callHttpGet(pathAndQuery), type);
    }

    /** GETs the given path+query (relative to the configured GitHub API base URL) and parses the JSON response. */
    public JsonNode callHttpGet(String pathAndQuery) throws Exception {
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.getApiBaseUrl() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET();
            String httpHeaderAuth = props.getHttpHeaderAuth();
            if (httpHeaderAuth != null && !httpHeaderAuth.isBlank()) {
                rb.header("Authorization", httpHeaderAuth);
            }

            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 200) return mapper.readTree(resp.body());

            if (sc == 403 && isPrimaryRateLimitExhausted(resp) && attempt <= 5) {
                long backoff = primaryRateLimitResetMs(resp);
                log.warn("GitHub primary rate limit exhausted on {}, retry {} in {}ms", pathAndQuery, attempt, backoff);
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

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
