package fr.an.projectanalysis.util;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Shared helpers of the "*ApiClient" classes calling a remote HTTP API with 429/5xx backoff and retry. */
public class HttpApiClientUtils {

    /** Cap of the exponential backoff, when the response has no "Retry-After" header. */
    private static final long MAX_BACKOFF_MILLIS = 60_000;

    private HttpApiClientUtils() {
    }

    /** URL-encodes a query parameter value (e.g. a JQL expression). */
    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** How long to wait before retrying: the response "Retry-After" header when present, or an
     * exponential backoff on the attempt number (2s, 4s, 8s, ... capped). */
    public static long retryAfterMs(HttpResponse<?> resp, int attempt) {
        return resp.headers().firstValue("Retry-After")
                .map(s -> Long.parseLong(s.trim()) * 1000L)
                .orElse((long) Math.min(MAX_BACKOFF_MILLIS, 1000L * (1L << attempt)));
    }

    /** Uninterruptible-looking sleep: restores the interrupt flag instead of throwing. */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
