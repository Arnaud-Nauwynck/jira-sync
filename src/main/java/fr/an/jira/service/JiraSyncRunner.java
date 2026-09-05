package fr.an.jira.service;

import fr.an.jira.configuration.JiraSyncProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Sync Jira Server/DC issues to local JSON files.
 * - paginated /search with fields=*all&expand=changelog
 * - re-fetches full comment list when truncated in search response
 * - re-fetches full changelog when truncated in search response
 * - 429 backoff + politeness delay
 */
@Component
public class JiraSyncRunner {

    private final JiraSyncProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public JiraSyncRunner(JiraSyncProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public void syncAll() throws Exception {
        Path out = props.out();
        Files.createDirectories(out);
        String jql = "project=" + props.project() + " ORDER BY key ASC";

        int start = 0;
        while (true) {
            JsonNode page = get("/rest/api/2/search"
                    + "?jql=" + enc(jql)
                    + "&startAt=" + start
                    + "&maxResults=" + props.step()
                    + "&fields=*all"
                    + "&expand=changelog");

            JsonNode issues = page.path("issues");
            if (!issues.isArray() || issues.isEmpty()) break;

            for (JsonNode n : issues) {
                ObjectNode issue = (ObjectNode) n;
                String key = issue.path("key").asText();
                completeComments(issue, key);
                completeChangelog(issue, key);
                Files.writeString(out.resolve(key + ".json"),
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(issue));
            }

            start += issues.size();
            System.out.printf("synced %d / %d%n", start, page.path("total").asInt());
            sleep(props.delayMs());
        }
        System.out.println("done -> " + out.toAbsolutePath());
    }

    /** fields.comment is capped in /search responses; re-fetch all when truncated. */
    private void completeComments(ObjectNode issue, String key) throws Exception {
        JsonNode c = issue.path("fields").path("comment");
        if (c.isMissingNode()) return;
        int total = c.path("total").asInt();
        int got = c.path("comments").size();
        if (got >= total) return;

        ArrayNode all = mapper.createArrayNode();
        int start = 0;
        while (start < total) {
            JsonNode page = get("/rest/api/2/issue/" + key
                    + "/comment?startAt=" + start + "&maxResults=100");
            JsonNode comments = page.path("comments");
            if (comments.isEmpty()) break;
            comments.forEach(all::add);
            start += comments.size();
            total = page.path("total").asInt(); // may move under our feet
            sleep(props.delayMs());
        }
        ObjectNode full = mapper.createObjectNode();
        full.set("comments", all);
        full.put("total", all.size());
        full.put("startAt", 0);
        full.put("maxResults", all.size());
        ((ObjectNode) issue.path("fields")).set("comment", full);
        System.out.printf("  %s: fetched %d comments%n", key, all.size());
    }

    /** changelog via expand on /search is capped; re-fetch the issue alone when truncated. */
    private void completeChangelog(ObjectNode issue, String key) throws Exception {
        JsonNode cl = issue.path("changelog");
        if (cl.isMissingNode()) return;
        int total = cl.path("total").asInt();
        int got = cl.path("histories").size();
        if (got >= total) return;

        // Server/DC has no paginated /issue/{key}/changelog endpoint (Cloud only):
        // a direct issue GET returns the full changelog.
        JsonNode fullIssue = get("/rest/api/2/issue/" + key + "?fields=key&expand=changelog");
        issue.set("changelog", fullIssue.path("changelog"));
        System.out.printf("  %s: fetched %d changelog entries%n",
                key, fullIssue.path("changelog").path("histories").size());
        sleep(props.delayMs());
    }

    private JsonNode get(String pathAndQuery) throws Exception {
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.base() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .header("Accept", "application/json")
                    .GET();
            if (props.pat() != null && !props.pat().isBlank()) {
                rb.header("Authorization", "Bearer " + props.pat());
            }

            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 200) return mapper.readTree(resp.body());
            if ((sc == 429 || sc >= 500) && attempt <= 5) {
                long backoff = retryAfterMs(resp, attempt);
                System.err.printf("HTTP %d on %s, retry %d in %dms%n", sc, pathAndQuery, attempt, backoff);
                sleep(backoff);
                continue;
            }
            throw new RuntimeException("HTTP " + sc + " on " + pathAndQuery + " : "
                    + resp.body().substring(0, Math.min(500, resp.body().length())));
        }
    }

    private static long retryAfterMs(HttpResponse<?> resp, int attempt) {
        return resp.headers().firstValue("Retry-After")
                .map(s -> Long.parseLong(s.trim()) * 1000L)
                .orElse((long) Math.min(60_000, 1000L * (1L << attempt))); // 2s,4s,8s,...
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
