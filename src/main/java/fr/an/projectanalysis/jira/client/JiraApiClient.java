package fr.an.projectanalysis.jira.client;

import fr.an.projectanalysis.jira.configuration.JiraSyncProperties;
import fr.an.projectanalysis.util.HttpApiClientUtils;
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

/**
 * Delegates all HTTP calls to the remote Jira Server/DC REST API.
 * - 429 / 5xx backoff with retry
 * - adds the configured Authorization header, if any
 */
@Component
@Slf4j
public class JiraApiClient {

    private final JiraSyncProperties props;

    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public JiraApiClient(JiraSyncProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** GETs the given path+query (relative to the configured Jira base URL) and parses the JSON response. */
    public JsonNode callHttpGet(String pathAndQuery) throws Exception {
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.getJiraApiServerBaseUrl() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .header("Accept", "application/json")
                    .GET();
            String httpHeaderAuth = props.getHttpHeaderAuth();
            if (httpHeaderAuth != null && !httpHeaderAuth.isBlank()) {
                rb.header("Authorization", httpHeaderAuth);
            }

            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 200) return mapper.readTree(resp.body());
            if ((sc == 429 || sc >= 500) && attempt <= 5) {
                long backoff = HttpApiClientUtils.retryAfterMs(resp, attempt);
                log.warn("HTTP {} on {}, retry {} in {}ms", sc, pathAndQuery, attempt, backoff);
                HttpApiClientUtils.sleep(backoff);
                continue;
            }
            throw new RuntimeException("HTTP " + sc + " on " + pathAndQuery + " : "
                    + resp.body().substring(0, Math.min(500, resp.body().length())));
        }
    }

}
