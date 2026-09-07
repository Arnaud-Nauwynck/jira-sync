package fr.an.jira.client;

import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures a real Jira REST API issue payload can be parsed into {@link SourceJiraIssueDTO}. */
class JiraIssueJsonParseTest {

    private static final Path SAMPLE_FILE = Path.of("src/test/data/issues/SPARK-12216.json");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldParseSampleIssueJson() throws IOException {
        String json = Files.readString(SAMPLE_FILE);

        SourceJiraIssueDTO issue = mapper.readValue(json, SourceJiraIssueDTO.class);

        assertThat(issue.key).isEqualTo("SPARK-12216");
        assertThat(issue.fields).isNotNull();
        assertThat(issue.fields.summary).isEqualTo("Spark failed to delete temp directory ");
        assertThat(issue.fields.status.name).isEqualTo("Resolved");
        assertThat(issue.fields.creator.displayName).isEqualTo("stefan");

        assertThat(issue.fields.issuelinks).hasSize(2);
        assertThat(issue.fields.issuelinks.get(0).inwardIssue.key).isEqualTo("SPARK-50628");

        assertThat(issue.fields.comment.comments).hasSize(30);

        assertThat(issue.changelog).isNotNull();
        assertThat(issue.changelog.histories).hasSize(5);
        SourceJiraIssueDTO.SourceJiraHistoryDTO lastHistory = issue.changelog.histories.get(issue.changelog.histories.size() - 1);
        assertThat(lastHistory.items.get(0).toString).isEqualTo("This issue is cloned by SPARK-50628");

        // per-project "customfield_XXXXX" entries are collected, not lost, without leaking known fields
        assertThat(issue.fields.customFields).containsEntry("customfield_12310420", "9223372036854775807");
        assertThat(issue.fields.customFields).doesNotContainKey("status");
    }
}
