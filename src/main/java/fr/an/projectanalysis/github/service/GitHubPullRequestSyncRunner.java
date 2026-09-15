package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.client.GitHubApiClient;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueCommentDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import fr.an.projectanalysis.github.mapper.SourceGitHubToAnnotatedPullRequestMapper;
import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Sync GitHub pull requests (all states) to local JSON files.
 * - no local sync state yet (first run): {@link #initialLoad()} walks every PR number from 1 up to
 *   the highest one currently known, fetching whichever aren't already stored locally
 * - otherwise: {@link #syncAllSince(Instant)} lists /pulls sorted by "updated desc", paginated,
 *   stopping early once a page's PRs are no longer newer than the last sync boundary
 * - fetches the single-PR detail endpoint for each new/changed PR (diff stats, mergeable state)
 * - 403/429/5xx backoff + politeness delay (see GitHubApiClient)
 * - the last successful run's start time is tracked in a local "pulls-sync-state.json" file
 */
@Component
public class GitHubPullRequestSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSyncRunner.class);

    private final GitHubSyncProperties props;

    private final ObjectMapper mapper;

    private final GitHubPullRequestRepository prRepository;

    private final GitHubApiClient apiClient;

    private final String baseRepoApiUrl;

    private final Path syncStateFile;

    private long syncGetByIdDelayMs;
    private long syncDelayMs;
    private Set<Integer> ignorePRNumbers;

    public GitHubPullRequestSyncRunner(GitHubSyncProperties props, ObjectMapper mapper,
                                       GitHubPullRequestRepository prRepository, GitHubApiClient apiClient) {
        this.props = props;
        this.mapper = mapper;
        this.prRepository = prRepository;
        this.apiClient = apiClient;
        this.syncStateFile = Path.of(props.getGithubSyncLocalDir(), "pulls-sync-state.json");
        this.syncGetByIdDelayMs = props.getSyncGetByIdDelayMs();
        this.syncDelayMs = props.getDelayMs();
        this.ignorePRNumbers = (props.getIgnorePRNumbers() != null)? new HashSet<>(props.getIgnorePRNumbers()) : null;
        this.baseRepoApiUrl = "/repos/" + props.getOrg() + "/" + props.getRepo();
    }

    /** Full or incremental sync of all pull requests (open, closed, merged) for the configured org/repo. */
    public void syncAll() throws Exception {
        long startMillis = System.currentTimeMillis();
        Instant syncStartTime = Instant.now();
        Instant since = loadLastSyncTime();

        int prChangeCount;
        if (since == null) {
            log.info("no local sync state: doing an initial load of {}/{}", props.getOrg(), props.getRepo());
            prChangeCount = initialLoad();
        } else {
            log.info("incremental sync: fetching pull requests updated since {}", since);
            prChangeCount = syncAllSince(since);
        }

        saveLastSyncTime(syncStartTime);
        if (prChangeCount > 0) {
            prRepository.compactAll();
        }

        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done syncAll, saved {} changes, took {} ms", prChangeCount, millis);
    }

    /**
     * Initial load, used when nothing has been synced yet. The "sort=updated desc" paginated
     * listing used by {@link #syncAllSince} is unsafe here: an initial load can take many pages,
     * and a PR updated while it's still in progress reorders itself towards the front of that
     * listing, which can cause page-based pagination to skip PRs entirely.
     * <p>
     * PR numbers, in contrast, are stable and monotonically assigned, so instead this walks every
     * number from 1 up to the highest one currently known (from a single "sort=created" lookup)
     * and fetches whichever aren't already stored locally — making the initial load resumable: if
     * it's interrupted partway through, PRs already saved are skipped on the next attempt.
     */
    private int initialLoad() throws Exception {
        int maxPrNumber = fetchMaxPrNumber();
        if (maxPrNumber <= 0) {
            log.info("no pull requests found for {}/{}", props.getOrg(), props.getRepo());
            return 0;
        }
        Map<Integer, Instant> alreadyLoaded = prRepository.listAllPRNumberWithUpdateTime();
        final int prToLoadCount = maxPrNumber - alreadyLoaded.size();
        log.info("initial load: scanning PR #1..#{}, {} already stored locally, remain to load: {}", maxPrNumber, alreadyLoaded.size(), prToLoadCount);

        int firstNumber = 1;
        int prChangeCount = 0;
        for (int number = firstNumber; number <= maxPrNumber; number++) {
            if (alreadyLoaded.containsKey(number)) {
                continue;
            }
            if (ignorePRNumbers != null && ignorePRNumbers.contains(number)) {
                continue;
            }
            try {
                SourceGitHubPullRequestDTO pr = fetchGithubPullRequestDetails(number);

                prRepository.save(pr);
                prChangeCount++;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg.contains("GitHub primary rate limit exhausted ... wait")) {
                    sleep(120_000);
                    syncGetByIdDelayMs += 15;
                    number--;
                    continue;
                }
                // PR numbers share the same sequence as plain issues, so a 404 here just means
                // #number is an issue, not a PR; log and move on rather than aborting the load.
                log.warn("  #{} : not a pull request, or failed to fetch, skipping: {}", number, e.toString());
            }
            sleep(syncGetByIdDelayMs);
            if (number % 100 == 0) {
                log.info("initial load progress: #{} ({}), saved [{}/{}] so far", number, maxPrNumber, prChangeCount, prToLoadCount);
            }
        }
        return prChangeCount;
    }

    /** The highest PR number currently known for the configured org/repo, or 0 if it has none. */
    private int fetchMaxPrNumber() throws Exception {
        JsonNode prs = apiClient.callHttpGet(baseRepoApiUrl + "/pulls"
                + "?state=all&sort=created&direction=desc&per_page=1&page=1");
        if (!prs.isArray() || prs.isEmpty()) {
            return 0;
        }
        return prs.get(0).path("number").asInt();
    }

    /**
     * Incremental sync, in two phases: first page through the /pulls list (sorted "updated desc")
     * to collect every PR number updated since {@code since}, then fetch and save each of them one
     * by one. Splitting it this way keeps the listing phase fast (list calls only, no per-PR detail
     * fetch in between), shrinking the window during which a PR updated mid-scan could reorder
     * across a page boundary and get skipped.
     */
    private int syncAllSince(Instant since) throws Exception {
        Map<Integer, Instant> toLoad = fetchPrNumbersUpdatedSince(since);
        log.info("incremental sync: {} pull requests updated since {}, loading them one by one", toLoad.size(), since);

        int prChangeCount = 0;
        for (int number : toLoad.keySet()) {
            SourceGitHubPullRequestDTO pr = fetchGithubPullRequestDetails(number);
            prRepository.save(pr);
            prChangeCount++;
            sleep(syncGetByIdDelayMs);
            if (prChangeCount % 100 == 0) {
                log.info("incremental sync progress: {} / {}", prChangeCount, toLoad.size());
            }
        }
        return prChangeCount;
    }

    /**
     * Backfills {@code reviewCommentsData} on PRs already persisted locally that are missing it
     * (e.g. synced before review-comment fetching was implemented). Walks every partition's PRs
     * and, for each with review comments ({@code reviewComments > 0}) but no data fetched yet,
     * fetches them via {@link #fetchPullRequestReviewComments} and resaves the PR.
     */
    public void completeMissingReviewComments() {
        long startMillis = System.currentTimeMillis();
        int completedCount = 0;
        for (int year : prRepository.findAllPartitionYears()) {
            List<GitHubPullRequestDTO> prs = prRepository.findByPartitionYear(year, pr ->
                    pr.reviewComments != null && pr.reviewComments > 0
                            && (pr.reviewCommentsData == null || pr.reviewCommentsData.isEmpty()));
            if (prs.isEmpty()) {
                continue;
            }
            log.info("completeMissingReviewComments for year:" + year + ", found " + prs.size() + " to complete");

            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> reviewComments =
                            fetchPullRequestReviewComments(pr.number, pr.reviewComments);
                    prRepository.putReviewComments(pr.number, SourceGitHubToAnnotatedPullRequestMapper.mapReviewComments(reviewComments));
                } catch(Exception ex) {
                    log.warn("Failed completeMissingReviewComments in fetchPullRequestReviewComments, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                // sleep(syncGetByIdDelayMs);
                if (completedCount % 100 == 0) {
                    log.info("completeMissingReviewComments progress for partition year {}: [{}/{}] PRs completed so far", year, completedCount, prs.size());
                }
            }
        }
        if (completedCount > 0) {
            prRepository.compactAll();
        }
        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done completeMissingReviewComments, completed {} PRs, took {} ms", completedCount, millis);
    }

    /**
     * Backfills {@code commentsData} on PRs already persisted locally that are missing it (e.g.
     * synced before issue-comment fetching was implemented). Walks every partition's PRs and, for
     * each with comments ({@code comments > 0}) but no data fetched yet, fetches them via
     * {@link #fetchIssueComments} and resaves the PR.
     */
    public void completeMissingComments() {
        long startMillis = System.currentTimeMillis();
        int completedCount = 0;
        for (int year : prRepository.findAllPartitionYears()) {
            List<GitHubPullRequestDTO> prs = prRepository.findByPartitionYear(year, pr ->
                    pr.comments != null && pr.comments > 0
                            && (pr.commentsData == null || pr.commentsData.isEmpty()));
            if (prs.isEmpty()) {
                continue;
            }
            log.info("completeMissingComments for year:" + year + ", found " + prs.size() + " to complete");

            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubIssueCommentDTO> comments = fetchIssueComments(pr.number, pr.comments);
                    prRepository.putComments(pr.number, SourceGitHubToAnnotatedPullRequestMapper.mapComments(comments));
                } catch(Exception ex) {
                    log.warn("Failed completeMissingComments in fetchIssueComments, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                // sleep(syncGetByIdDelayMs);
                if (completedCount % 100 == 0) {
                    log.info("completeMissingComments progress for partition year {}: [{}/{}] PRs completed so far", year, completedCount, prs.size());
                }
            }
        }
        if (completedCount > 0) {
            prRepository.compactAll();
        }
        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done completeMissingComments, completed {} PRs, took {} ms", completedCount, millis);
    }

    private SourceGitHubPullRequestDTO fetchGithubPullRequestDetails(int number) throws Exception {
        SourceGitHubPullRequestDTO pr = apiClient.callHttpGet(baseRepoApiUrl + "/pulls/" + number, SourceGitHubPullRequestDTO.class);
        if (pr.reviewComments != null && pr.reviewComments > 0) {
            pr.reviewCommentsData = fetchPullRequestReviewComments(number, pr.reviewComments);
        }
        return pr;
    }

    /** Pages through /pulls/{number}/comments (PR review comments), sorted "created desc", until an empty page. */
    private List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> fetchPullRequestReviewComments(int number, int reviewCommentsCount) throws Exception {
        List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> result = new ArrayList<>();
        int page = 1;
        int fetchedCount = 0;
        while (true) {
            JsonNode comments = apiClient.callHttpGet(baseRepoApiUrl + "/pulls/" + number + "/comments"
                    + "?sort=created&direction=desc&per_page=100&page=" + page);
            if (!comments.isArray() || comments.isEmpty()) {
                break;
            }
            for (JsonNode n : comments) {
                result.add(mapper.treeToValue(n, SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO.class));
                fetchedCount++;
            }
            if (fetchedCount >= reviewCommentsCount) {
                break;
            }
            page++;
            // sleep(syncGetByIdDelayMs);
        }
        if ((1 + reviewCommentsCount) == result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} review comments, missing 1", number, reviewCommentsCount);
        } else if (reviewCommentsCount != result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} review comments, got {}", number, reviewCommentsCount, result.size());
        }
        return result;
    }

    /** Pages through /issues/{number}/comments (issue/conversation comments), sorted "created desc", until an empty page. */
    private List<SourceGitHubIssueCommentDTO> fetchIssueComments(int number, int commentsCount) throws Exception {
        List<SourceGitHubIssueCommentDTO> result = new ArrayList<>();
        int page = 1;
        int fetchedCount = 0;
        while (true) {
            JsonNode comments = apiClient.callHttpGet(baseRepoApiUrl + "/issues/" + number + "/comments"
                    + "?sort=created&direction=asc&per_page=100&page=" + page);
            if (!comments.isArray() || comments.isEmpty()) {
                break;
            }
            for (JsonNode n : comments) {
                result.add(mapper.treeToValue(n, SourceGitHubIssueCommentDTO.class));
                fetchedCount++;
            }
            if (fetchedCount >= commentsCount) {
                break;
            }
            page++;
            // sleep(syncGetByIdDelayMs);
        }
        if ((1 + commentsCount) == result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} comments, missing 1", number, commentsCount);
        } else if (commentsCount != result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} comments, got {}", number, commentsCount, result.size());
        }
        return result;
    }

    /**
     * Pages through /pulls sorted "updated desc", collecting the number and updatedAt of every PR
     * updated since {@code since}, stopping once a page's PRs are no longer newer.
     */
    private Map<Integer, Instant> fetchPrNumbersUpdatedSince(Instant since) throws Exception {
        Map<Integer, Instant> result = new LinkedHashMap<>();
        int page = 1;
        outer:
        while (true) {
            JsonNode prs = apiClient.callHttpGet(baseRepoApiUrl + "/pulls"
                    + "?state=all&sort=created&direction=asc"
                    + "&per_page=" + props.getPerPage()
                    + "&page=" + page);

            if (!prs.isArray() || prs.isEmpty()) {
                break;
            }

            for (JsonNode n : prs) {
                Instant updatedAt = Instant.parse(n.path("updated_at").asText());
                if (!updatedAt.isAfter(since)) {
                    // sorted "updated desc": everything from here on is already synced
                    break outer;
                }
                result.put(n.path("number").asInt(), updatedAt);
            }

            log.info("listing pull requests updated since {}: {} found so far (page {})", since, result.size(), page);
            page++;
            sleep(syncDelayMs);
        }
        return result;
    }

    /** Reads the start time of the last successful run, or null if none / unreadable. */
    public Instant loadLastSyncTime() {
        if (!Files.exists(syncStateFile)) {
            return null;
        }
        try {
            JsonNode state = mapper.readTree(syncStateFile.toFile());
            String s = state.path("lastSyncTime").asText(null);
            return s != null ? Instant.parse(s) : null;
        } catch (Exception e) {
            log.warn("failed to read sync state file {}, falling back to full sync: {}",
                    syncStateFile, e.toString());
            return null;
        }
    }

    /** Records the start time of this run, used as the "updated since" bound for the next incremental run. */
    private void saveLastSyncTime(Instant syncStartTime) throws Exception {
        ObjectNode state = mapper.createObjectNode();
        state.put("lastSyncTime", syncStartTime.toString());
        Files.createDirectories(Path.of(props.getGithubSyncLocalDir()));
        Files.writeString(syncStateFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

}
