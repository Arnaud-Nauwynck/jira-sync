package fr.an.jira.mcp;

import fr.an.jira.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.jira.github.service.GitHubPullRequestSyncRunner;
import fr.an.jira.github.service.GitHubPullRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tools exposing the local GitHub pull-request mirror ({@link GitHubPullRequestService},
 * {@link GitHubPullRequestSyncRunner}).
 */
@Component
@Slf4j
public class GitHubPrMcpTools {

    private final GitHubPullRequestService pullRequestService;
    private final GitHubPullRequestSyncRunner gitHubPrSyncRunner;

    public GitHubPrMcpTools(GitHubPullRequestService pullRequestService, GitHubPullRequestSyncRunner gitHubPrSyncRunner) {
        this.pullRequestService = pullRequestService;
        this.gitHubPrSyncRunner = gitHubPrSyncRunner;
    }

    @Tool(description = "Find a single locally-synced GitHub pull request by its number. Returns null if not found.")
    public GitHubPullRequestDTO findPullRequestByNumber(
            @ToolParam(description = "Pull request number, e.g. 123") int number) {
        log.info("mcp tool findPullRequestByNumber({})", number);
        return pullRequestService.findByNumber(number);
    }

    @Tool(description = "List locally-synced GitHub pull requests created between fromYear and toYear (inclusive), optionally filtered by author login regex")
    public List<GitHubPullRequestDTO> queryPullRequests(
            @ToolParam(description = "Earliest creation year, inclusive") int fromYear,
            @ToolParam(description = "Latest creation year, inclusive") int toYear,
            @ToolParam(description = "Optional regex to filter by author login", required = false) String usernamePattern) {
        log.info("mcp tool queryPullRequests(fromYear={}, toYear={}, usernamePattern={})", fromYear, toYear, usernamePattern);
        return pullRequestService.queryPullRequests(fromYear, toYear, usernamePattern);
    }

    @Tool(description = "Run the GitHub pull-request synchronization for the configured org/repo, pulling new/updated pull requests from GitHub into the local mirror.")
    public String runGitHubSyncAll() throws Exception {
        log.info("mcp tool runGitHubSyncAll()");
        long startTime = System.currentTimeMillis();
        gitHubPrSyncRunner.syncAll();
        int millis = (int) (System.currentTimeMillis() - startTime);
        return "sync completed in " + millis + " ms";
    }

}
