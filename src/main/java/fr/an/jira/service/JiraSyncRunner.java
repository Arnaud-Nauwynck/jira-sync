package fr.an.jira.service;

import fr.an.jira.client.JiraApiClient;
import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.configuration.JiraSyncProperties;
import fr.an.jira.repository.JiraIssueRepository;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * Sync Jira Server/DC issues to local JSON files.
 * - paginated /search with fields=*all&expand=changelog
 * - re-fetches full comment list when truncated in search response
 * - re-fetches full changelog when truncated in search response
 * - 429 backoff + politeness delay
 * - incremental mode: skips issues not modified since the last successful run,
 *   tracked in a local "sync-state.json" file
 */
@Component
public class JiraSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncRunner.class);

    private final JiraSyncProperties props;

    private final ObjectMapper mapper;

    private final JiraIssueRepository issueRepository;

    private final JiraApiClient apiClient;

    private static final DateTimeFormatter JQL_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Path jiraStateFile;

    private long syncDelayMs;

    public JiraSyncRunner(JiraSyncProperties props, ObjectMapper mapper, JiraIssueRepository issueRepository,
                           JiraApiClient apiClient) {
        this.props = props;
        this.mapper = mapper;
        this.issueRepository = issueRepository;
        this.apiClient = apiClient;
        this.jiraStateFile = Path.of(props.getJiraSyncLocalDir(), "sync-state.json");
        this.syncDelayMs = props.getDelayMs();
    }

    /** Full sync */
    public void syncAll() throws Exception {
        long startMillis = System.currentTimeMillis();
        Instant syncStartTime = Instant.now();
        Instant since = loadLastSyncTime();

        String jql = "project=" + props.getProject();
        if (since != null) {
            jql += " AND updated >= \"" + JQL_DATE_FMT.format(since) + "\"";
            log.info("incremental sync: fetching issues updated since {}", since);
        } else {
            log.info("full sync: fetching all issues");
        }
        jql += " ORDER BY key ASC";

        int issueChangeCount = 0;
        int start = 0;
        while (true) {
            JsonNode page = apiClient.callHttpGet("/rest/api/2/search"
                    + "?jql=" + JiraApiClient.enc(jql)
                    + "&startAt=" + start
                    + "&maxResults=" + props.getMaxResults()
                    + "&fields=*all"
                    + "&expand=changelog");

            JsonNode issues = page.path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                break;
            }

            for (JsonNode n : issues) {
                ObjectNode issueNode = (ObjectNode) n;
                SourceJiraIssueDTO issue = mapper.treeToValue(issueNode, SourceJiraIssueDTO.class);
                String key = issue.key;;
                fixMalformedCreatedYear(issue, key);
                completeComments(issue, key);
                completeChangelog(issue, key);
                issueRepository.save(issue);
                issueChangeCount++;
            }

            start += issues.size();
            log.info("synced {} / {}", start, page.path("total").asInt());
            sleep(syncDelayMs);
        }

        saveLastSyncTime(syncStartTime);
        if (issueChangeCount > 0) {
            issueRepository.compactAll();
        }

        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done syncAll, saved {} changes, took {} ms", issueChangeCount, millis);
    }

    /** Reads the start time of the last successful run, or null if none / unreadable. */
    private Instant loadLastSyncTime() {
        if (!Files.exists(jiraStateFile)) {
            return null;
        }
        try {
            JsonNode state = mapper.readTree(jiraStateFile.toFile());
            String s = state.path("lastSyncTime").asText(null);
            return s != null ? Instant.parse(s) : null;
        } catch (Exception e) {
            log.warn("failed to read sync state file {}, falling back to full sync: {}",
                    jiraStateFile, e.toString());
            return null;
        }
    }

    /** Records the start time of this run, used as the "updated since" bound for the next incremental run. */
    private void saveLastSyncTime(Instant syncStartTime) throws Exception {
        ObjectNode state = mapper.createObjectNode();
        state.put("lastSyncTime", syncStartTime.toString());
        Files.writeString(jiraStateFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    }

    /** fields.comment is capped in /search responses; re-fetch all when truncated. */
    private void completeComments(SourceJiraIssueDTO issue, String key) throws Exception {
        SourceJiraIssueDTO.SourceJiraCommentsDTO comment = issue.fields.comment;
        if (null == comment) return;
        int total = comment.total;
        int got = comment.comments.size();
        if (got >= total) return;

        val all = new ArrayList<SourceJiraIssueDTO.SourceJiraCommentDTO>();
        int start = 0;
        while (start < total) {
            JsonNode page = apiClient.callHttpGet("/rest/api/2/issue/" + key
                    + "/comment?startAt=" + start + "&maxResults=100");
            JsonNode comments = page.path("comments");
            if (comments.isEmpty()) break;
            comments.forEach(commentNode -> {
                val item = mapper.treeToValue(commentNode, SourceJiraIssueDTO.SourceJiraCommentDTO.class);
                all.add(item);
            });
            start += comments.size();
            total = page.path("total").asInt(); // may move under our feet
            sleep(syncDelayMs);
        }
        comment.comments.clear();
        comment.comments.addAll(all);
        comment.total = all.size();
        comment.startAt = 0;
        comment.maxResults = all.size();
        log.info("  {} : fetched {} comments", key, all.size());
    }

    /** changelog via expand on /search is capped; re-fetch the issue alone when truncated. */
    private void completeChangelog(SourceJiraIssueDTO issue, String key) throws Exception {
        val changelog = issue.changelog;
        if (changelog == null) return;
        int total = changelog.total;
        int got = changelog.histories.size();
        if (got >= total) return;

        // Server/DC has no paginated /issue/{key}/changelog endpoint (Cloud only):
        // a direct issue GET returns the full changelog.
        JsonNode fullIssue = apiClient.callHttpGet("/rest/api/2/issue/" + key + "?fields=key&expand=changelog");
        JsonNode fullChangelogNode = fullIssue.path("changelog");
        val fullChangeLog = mapper.treeToValue(fullChangelogNode, SourceJiraIssueDTO.SourceJiraChangelogDTO.class);
        issue.changelog = fullChangeLog;
        log.info("  {} : fetched {} changelog entries", key, fullChangeLog.histories.size());
        sleep(syncDelayMs);
    }

    /**
     * Some very old issues have their "fields.created" year mis-recorded with the century
     * dropped, e.g. "12-03-15T10:00:00.000+0000" or "0012-03-15T10:00:00.000+0000" instead of
     * "2012-03-15T10:00:00.000+0000". Left as-is, {@link JiraIssueRepository#save} would file the
     * issue into a bogus "created_year=12"/"created_year=0012" partition instead of
     * "created_year=2012".
     */
    private int fixMalformedCreatedYear(SourceJiraIssueDTO issue, String key) {
        val fields = issue.fields;
        String created = fields.created;
        int dash = created.indexOf('-');
        if (dash <= 0) return 0;
        String yearPart = created.substring(0, dash);
        int year;
        try {
            year = Integer.parseInt(yearPart);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (year < 1000) {
            int fixedYear = year + 2000;
            fields.created = String.format("%04d", fixedYear) + created.substring(dash);
            log.warn("  {} : fixed malformed created year \"{}\" -> {}", key, yearPart, fixedYear);
        }
        return year;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
