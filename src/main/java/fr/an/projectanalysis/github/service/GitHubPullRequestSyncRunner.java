package fr.an.projectanalysis.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.an.projectanalysis.github.client.GitHubApiClient;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestCommitDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueCommentDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueEventDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import fr.an.projectanalysis.github.mapper.SourceGitHubToAnnotatedPullRequestMapper;
import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
@Slf4j
public class GitHubPullRequestSyncRunner {

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
                SourceGitHubPullRequestDTO sourcePr = fetchGithubPullRequestDetails(number);
                GitHubPullRequestDTO pr = SourceGitHubToAnnotatedPullRequestMapper.from(sourcePr);

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class NumberHolderDTO {
        public int number;
    }

    /** The highest PR number currently known for the configured org/repo, or 0 if it has none. */
    private int fetchMaxPrNumber() throws Exception {
        List<NumberHolderDTO> prs = apiClient.callHttpGet_List(baseRepoApiUrl + "/pulls"
                + "?state=all&sort=created&direction=desc&per_page=1&page=1", NumberHolderDTO.class);
        if (prs.isEmpty()) {
            return 0;
        }
        return prs.get(0).number;
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
            if (ignorePRNumbers != null && ignorePRNumbers.contains(number)) {
                continue;
            }
            try {
                // TODO avoid querying twice the data from Rest API, cf data also loaded in fetchPrNumbersUpdatedSince() but only number was used
                SourceGitHubPullRequestDTO sourcePr = fetchGithubPullRequestDetails(number);
                GitHubPullRequestDTO previousPrOrNull = prRepository.findByNumber(number);
                GitHubPullRequestDTO pr = SourceGitHubToAnnotatedPullRequestMapper.from(sourcePr);

                if (previousPrOrNull == null) {
                    prRepository.save(pr);
                } else {
                    // TODO avoid save overwriting all ... restore previous annotated data if available
                    pr.annotated = previousPrOrNull.annotated;
//                    prRepository.mutateIssue(number, toUpdate -> {
//                       // TOCHECK: should rather copy all fields?
//                    });
                    prRepository.save(pr);
                }
                prChangeCount++;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("GitHub primary rate limit exhausted ... wait")) {
                    sleep(120_000);
                    syncGetByIdDelayMs += 15;
                    continue;
                }
                log.warn("  #{} : failed to fetch, skipping: {}", number, e.toString());
                continue;
            }
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
            sleep(syncDelayMs);

            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> reviewComments =
                            fetchPullRequestReviewComments(pr.number, pr.reviewComments);
                    List<GitHubPullRequestReviewCommentDTO> reviewCommentsData = SourceGitHubToAnnotatedPullRequestMapper.mapReviewComments(reviewComments);
                    prRepository.mutateIssue(pr.number, toUpdate -> {
                        toUpdate.reviewCommentsData = reviewCommentsData;
                        if (reviewCommentsData != null && reviewCommentsData.size() != pr.comments) {
                            toUpdate.reviewComments = reviewCommentsData.size(); // workaround: correct source, to avoid re-fetch again
                        }
                    });
                } catch(Exception ex) {
                    log.warn("Failed completeMissingReviewComments in fetchPullRequestReviewComments, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                sleep(syncGetByIdDelayMs);
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
        int completedTotalCount = 0;
        for (int year : prRepository.findAllPartitionYears()) {
            List<GitHubPullRequestDTO> prs = prRepository.findByPartitionYear(year, pr ->
                    pr.comments != null && pr.comments > 0
                            && (pr.commentsData == null || pr.commentsData.isEmpty()));
            if (prs.isEmpty()) {
                continue;
            }
            log.info("completeMissingComments for year:" + year + ", found " + prs.size() + " to complete");

            int completedCount = 0;
            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubIssueCommentDTO> comments = fetchIssueComments(pr.number, pr.comments);
                    List<GitHubIssueCommentDTO> commentsData = SourceGitHubToAnnotatedPullRequestMapper.mapComments(comments);
                    prRepository.mutateIssue(pr.number, toUpdate -> {
                        toUpdate.commentsData = commentsData;
                        if (commentsData != null && commentsData.size() != pr.comments) {
                            toUpdate.comments = commentsData.size(); // workaround: correct source, to avoid re-fetch again
                        }
                    });
                } catch(Exception ex) {
                    log.warn("Failed completeMissingComments in fetchIssueComments, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                sleep(syncGetByIdDelayMs);
                if (completedCount % 100 == 0) {
                    log.info("completeMissingComments progress for partition year {}: [{}/{}] PRs completed so far", year, completedCount, prs.size());
                }
            }
            completedTotalCount += completedCount;
        }
        if (completedTotalCount > 0) {
            prRepository.compactAll();
        }
        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done completeMissingComments, completed {} PRs, took {} ms", completedTotalCount, millis);
    }

    /**
     * Backfills {@code issueEventsData} on PRs already persisted locally that are missing it (e.g.
     * synced before issue-event fetching was implemented). Walks every partition's PRs and, for
     * each with no data fetched yet, fetches them via {@link #fetchIssueEvents} and resaves the PR.
     * Unlike comments/review-comments, GitHub does not report an issue-events count on the PR
     * detail response, so "missing" here just means {@code issueEventsData == null}.
     */
    public void completeMissingIssueEvents() {
        long startMillis = System.currentTimeMillis();
        int completedTotalCount = 0;
        for (int year : prRepository.findAllPartitionYears()) {
            List<GitHubPullRequestDTO> prs = prRepository.findByPartitionYear(year, pr -> pr.issueEventsData == null);
            if (prs.isEmpty()) {
                continue;
            }
            log.info("completeMissingIssueEvents for year:" + year + ", found " + prs.size() + " to complete");
            sleep(syncDelayMs);

            int completedCount = 0;
            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubIssueEventDTO> issueEvents = fetchIssueEvents(pr.number);
                    List<GitHubIssueEventDTO> issueEventsData = SourceGitHubToAnnotatedPullRequestMapper.mapIssueEvents(issueEvents);
                    prRepository.mutateIssue(pr.number, pr1 -> pr1.issueEventsData = issueEventsData);
                } catch(Exception ex) {
                    log.warn("Failed completeMissingIssueEvents in fetchIssueEvents, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                sleep(syncGetByIdDelayMs);
                if (completedCount % 100 == 0) {
                    log.info("completeMissingIssueEvents progress for partition year {}: [{}/{}] PRs completed so far", year, completedCount, prs.size());
                }
            }
            completedTotalCount += completedCount;
        }
        if (completedTotalCount > 0) {
            prRepository.compactAll();
        }
        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done completeMissingIssueEvents, completed {} PRs, took {} ms", completedTotalCount, millis);
    }


    /**
     * Backfills {@code commitsData} on PRs already persisted locally that are missing
     */
    public void completeMissingPullRequestCommits() {
        long startMillis = System.currentTimeMillis();
        int completedTotalCount = 0;
        for (int year : prRepository.findAllPartitionYears()) {
            List<GitHubPullRequestDTO> prs = prRepository.findByPartitionYear(year, pr ->
                    pr.commits != null && pr.commits > 0 && (pr.commitsData2 == null || pr.commits == pr.commitsData2.size()));
            if (prs.isEmpty()) {
                continue;
            }
            log.info("complete missing PullRequest Commits for year:" + year + ", found " + prs.size() + " to complete");
            sleep(syncDelayMs);

            int completedCount = 0;
            for (GitHubPullRequestDTO pr : prs) {
                try {
                    List<SourceGitHubPullRequestCommitDTO> sourceCommitsData = fetchPullRequestCommits(pr.number, pr.commits);
                    List<GitHubPullRequestCommitDTO> commitsData2 = SourceGitHubToAnnotatedPullRequestMapper.mapCommits(sourceCommitsData);
                    prRepository.mutateIssue(pr.number, toUpdate -> {
                        toUpdate.commitsData = null; // clear deprecated field
                        toUpdate.commitsData2 = commitsData2;
                        if (commitsData2.size() != pr.commits) {
                            log.warn("MISMATCH PR #" + pr.number + " expecting " + pr.commits + " commits, got " + commitsData2.size());
                        }
                    });
                } catch(Exception ex) {
                    log.warn("Failed completeMissingIssueCommits in fetchIssueCommits, for #{} ... ignore, no rethrow!", pr.number, ex);
                    sleep(syncDelayMs);
                    // ignore, no rethrow!
                }

                completedCount++;
                sleep(syncGetByIdDelayMs);
                if (completedCount % 100 == 0) {
                    log.info("completeMissingIssueCommits progress for partition year {}: [{}/{}] PRs completed so far", year, completedCount, prs.size());
                }
            }
            completedTotalCount += completedCount;
        }
        if (completedTotalCount > 0) {
            prRepository.compactAll();
        }
        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done completeMissingIssueCommits, completed {} PRs, took {} ms", completedTotalCount, millis);
    }


    private SourceGitHubPullRequestDTO fetchGithubPullRequestDetails(int number) throws Exception {
        SourceGitHubPullRequestDTO pr = apiClient.callHttpGet(baseRepoApiUrl + "/pulls/" + number, SourceGitHubPullRequestDTO.class);
        log.info("loading PR #{} createdAt: {} updatedAt:{}, details (comments:{}, reviewComments:{}, commits:{}, events)",
                number, pr.getCreatedAt(), pr.getUpdatedAt(),
                pr.comments, pr.reviewComments, pr.commits);
        if (pr.comments != null && pr.comments > 0) {
            pr.commentsData = fetchIssueComments(number, pr.comments);
            if (pr.commentsData.size() != pr.comments) {
                pr.comments = pr.commentsData.size(); // workaround: correct source, to avoid re-fetch again
            }
        }
        if (pr.reviewComments != null && pr.reviewComments > 0) {
            pr.reviewCommentsData = fetchPullRequestReviewComments(number, pr.reviewComments);
            if (pr.reviewCommentsData.size() != pr.reviewComments) {
                pr.reviewComments = pr.reviewCommentsData.size(); // workaround: correct source, to avoid re-fetch again
            }
        }
        if (pr.commits != null && pr.commits > 0) {
            try {
                pr.commitsData = fetchPullRequestCommits(pr.number, pr.commits);
                if (pr.commitsData.size() != pr.commits) {
                    pr.commits = pr.commitsData.size(); // workaround: correct source, to avoid re-fetch again
                }
            } catch(Exception ex) {
                log.warn("Failed to get commits for PR #{} .. ignore, norethrow! ex:{}", pr.number, ex.getMessage());
            }
        }
        pr.issueEventsData = fetchIssueEvents(pr.number);
        return pr;
    }

    /** Pages through /pulls/{number}/comments (PR review comments), sorted "created desc", until an empty page. */
    private List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> fetchPullRequestReviewComments(int number, int reviewCommentsCount) throws Exception {
        List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> result = new ArrayList<>();
        int page = 1;
        int fetchedCount = 0;
        while (true) {
            val comments = apiClient.callHttpGet_List(baseRepoApiUrl + "/pulls/" + number + "/comments"
                    + "?sort=created&direction=desc&per_page=100&page=" + page,
                    SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO.class);
            for (SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO comment : comments) {
                // may convert
                result.add(comment);
                fetchedCount++;
            }
            if (fetchedCount >= reviewCommentsCount) {
                break;
            }
            page++;
            sleep(syncGetByIdDelayMs);
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
            val comments = apiClient.callHttpGet_List(baseRepoApiUrl + "/issues/" + number + "/comments"
                    + "?sort=created&direction=asc&per_page=100&page=" + page,
                    SourceGitHubIssueCommentDTO.class);
            if (comments.isEmpty()) {
                break;
            }
            // TOADD may convert
            result.addAll(comments);
            fetchedCount += comments.size();
            if (fetchedCount >= commentsCount) {
                break;
            }
            page++;
            sleep(syncGetByIdDelayMs);
        }
        if ((1 + commentsCount) == result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} comments, missing 1", number, commentsCount);
        } else if (commentsCount != result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} comments, got {}", number, commentsCount, result.size());
        }
        return result;
    }


    /**
     * http GET "/repos/{owner}/{repo}/pulls/{pull_number}/commits"
     * see https://docs.github.com/en/rest/pulls/pulls?apiVersion=2026-03-10#list-commits-on-a-pull-request
     */
    private List<SourceGitHubPullRequestCommitDTO> fetchPullRequestCommits(int number, int commitsCount) throws Exception {
        List<SourceGitHubPullRequestCommitDTO> result = new ArrayList<>();
        int page = 1;
        int fetchedCount = 0;
        while (true) {
            val commits = apiClient.callHttpGet_List(baseRepoApiUrl + "/pulls/" + number + "/commits"
                            + "?per_page=100&page=" + page,
                    SourceGitHubPullRequestCommitDTO.class);
            if (commits.isEmpty()) {
                break;
            }
            // TOADD may convert
            result.addAll(commits);
            fetchedCount += commits.size();
            if (fetchedCount >= commitsCount) {
                break;
            }
            page++;
            sleep(syncGetByIdDelayMs);
        }
        if ((1 + commitsCount) == result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} commits, missing 1", number, commitsCount);
        } else if (commitsCount != result.size()) {
            log.warn("Unexpected mismatch for Github PR #{}, expecting {} commits, got {}", number, commitsCount, result.size());
        }
        return result;
    }

    /** Pages through /issues/{number}/events (issue/timeline events), until an empty page. */
    private List<SourceGitHubIssueEventDTO> fetchIssueEvents(int number) throws Exception {
        List<SourceGitHubIssueEventDTO> result = new ArrayList<>();
        int page = 1;
        while (true) {
            val events = apiClient.callHttpGet_List(baseRepoApiUrl + "/issues/" + number + "/events"
                    + "?per_page=100&page=" + page, SourceGitHubIssueEventDTO.class);
            if (events.isEmpty()) {
                break;
            }
            result.addAll(events);
            page++;
            sleep(syncGetByIdDelayMs);
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class NumberAndUpdatedAtHolderDTO {
        public int number;
        // Instant
        public String updated_at;
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
            val prs = apiClient.callHttpGet_List(baseRepoApiUrl + "/pulls"
                    + "?state=all&sort=updated&direction=desc"
                    + "&per_page=" + props.getPerPage()
                    + "&page=" + page,
                    NumberAndUpdatedAtHolderDTO.class);
            if (prs.isEmpty()) {
                break;
            }
            for (val pr: prs) {
                Instant updatedAt = Instant.parse(pr.updated_at);
                if (!updatedAt.isAfter(since)) {
                    // sorted "updated desc": everything from here on is already synced
                    break outer;
                }
                result.put(pr.number, updatedAt);
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
