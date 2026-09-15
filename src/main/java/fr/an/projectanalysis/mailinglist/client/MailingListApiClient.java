package fr.an.projectanalysis.mailinglist.client;

import fr.an.projectanalysis.mailinglist.client.dtos.SourceMailMessageDTO;
import fr.an.projectanalysis.mailinglist.configuration.MailingListSyncProperties;
import fr.an.projectanalysis.mailinglist.mapper.MimeMessageToSourceMailMessageMapper;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    private static final Logger log = LoggerFactory.getLogger(MailingListApiClient.class);

    private final MailingListSyncProperties props;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MailingListApiClient(MailingListSyncProperties props) {
        this.props = props;
    }

    /** Downloads and parses the messages for one calendar month ("yyyy-MM"), or an empty list if none archived. */
    public List<SourceMailMessageDTO> fetchMbox(String yearMonth) throws Exception {
        byte[] mboxBytes = fetchMboxBytes(yearMonth);
        List<byte[]> rawMessages = MboxMessageSplitter.splitMessages(mboxBytes);

        List<SourceMailMessageDTO> result = new ArrayList<>(rawMessages.size());
        for (byte[] raw : rawMessages) {
            try {
                Message mimeMessage = Message.Builder.of()
                        .use(MimeConfig.PERMISSIVE)
                        .parse(new ByteArrayInputStream(raw))
                        .build();
                SourceMailMessageDTO dto = MimeMessageToSourceMailMessageMapper.from(mimeMessage);
                if (dto.messageId == null || dto.messageId.isBlank()) {
                    log.warn("  {} : skipping a message with no Message-ID", yearMonth);
                    continue;
                }
                result.add(dto);
            } catch (Exception e) {
                log.warn("  {} : failed to parse a message, skipping: {}", yearMonth, e.toString());
            }
        }
        return result;
    }

    /** Downloads the raw mbox bytes for one calendar month ("yyyy-MM"), or an empty array if none archived. */
    private byte[] fetchMboxBytes(String yearMonth) throws Exception {
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
