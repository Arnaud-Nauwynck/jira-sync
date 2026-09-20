package fr.an.projectanalysis.service;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptBatchStart;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * A single change detected while syncing a source (Jira, GitHub, mailing-list), recorded by
 * {@link RecentChangeLogService} for display as a recent-activity feed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JiraIssueChange.class, name = "jiraIssue"),
        @JsonSubTypes.Type(value = GithubPRChange.class, name = "githubPR"),
        @JsonSubTypes.Type(value = MailingListChange.class, name = "mailingList"),
        @JsonSubTypes.Type(value = ClaudeCodePromptBatchStart.class, name = "claudeCodePrompt"),
        @JsonSubTypes.Type(value = McpToolCallChange.class, name = "mcpToolCall")
})
@Getter
public abstract class ChangeLogEvent {

    protected final LocalDateTime timestamp;

    /** Monotonically increasing, assigned by {@link RecentChangeLogService#addEvent} when queued;
     * 0 until then. Lets REST clients poll for events after the highest {@code seq} they've seen. */
    private long seq;

    protected ChangeLogEvent() {
        this(LocalDateTime.now());
    }

    protected ChangeLogEvent(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    void assignSeq(long seq) {
        this.seq = seq;
    }

    /** Short human-readable summary of the change, for display in a recent-activity feed. */
    public abstract String summary();

}
