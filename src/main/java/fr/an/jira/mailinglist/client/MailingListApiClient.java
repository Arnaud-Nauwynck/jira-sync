package fr.an.jira.mailinglist.client;

import fr.an.jira.mailinglist.configuration.MailingListSyncProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Delegates HTTP calls to the lists.apache.org monthly mbox archive API
 * ({@code /api/mbox.lua?list=...&domain=...&d=yyyy-MM&q=}).
 * - 429 / 5xx backoff with retry
 * - a 404 (no archive for that month, e.g. a future month, or before the list existed) is treated
 *   as "no messages" rather than an error
 * - adds the configured Authorization header, if any (the archive is public; usually left unset)
 */
@Component
public class MailingListApiClient {

    private final MailingListSyncProperties props;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MailingListApiClient(MailingListSyncProperties props) {
        this.props = props;
    }

    /** Downloads the raw mbox bytes for one calendar month ("yyyy-MM"), or an empty array if none archived. */
    public byte[] fetchMbox(String yearMonth) throws Exception {
        String pathAndQuery = "/api/mbox.lua?list=" + enc(props.getList())
                + "&domain=" + enc(props.getDomain())
                + "&d=" + enc(yearMonth)
                + "&q=";
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.getApiBaseUrl() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .GET();
            String httpHeaderAuth = props.getHttpHeaderAuth();
            if (httpHeaderAuth != null && !httpHeaderAuth.isBlank()) {
                rb.header("Authorization", httpHeaderAuth);
            }

            HttpResponse<byte[]> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            if (sc == 200) return resp.body();
            if (sc == 404) return new byte[0];
            if ((sc == 429 || sc >= 500) && attempt <= 5) {
                long backoff = retryAfterMs(resp, attempt);
                System.err.printf("HTTP %d on %s, retry %d in %dms%n", sc, pathAndQuery, attempt, backoff);
                sleep(backoff);
                continue;
            }
            throw new RuntimeException("HTTP " + sc + " on " + pathAndQuery);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
