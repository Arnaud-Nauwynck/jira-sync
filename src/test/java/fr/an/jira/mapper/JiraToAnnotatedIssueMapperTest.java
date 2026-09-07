package fr.an.jira.mapper;

import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures a real Jira REST API issue payload is correctly flattened by {@link SourceJiraToAnnotatedIssueMapper}. */
class JiraToAnnotatedIssueMapperTest {

    private static final Path SAMPLE_FILE = Path.of("src/test/data/issues/SPARK-12216.json");
    private static final Path OUTPUT_FILE = Path.of("target/test/issues/annotated-SPARK-12216.json");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldMapSampleIssueJsonToAnnotatedDTO() throws IOException {
        String json = Files.readString(SAMPLE_FILE);
        SourceJiraIssueDTO issue = mapper.readValue(json, SourceJiraIssueDTO.class);

        JiraIssueDTO annotated = SourceJiraToAnnotatedIssueMapper.from(issue);

        Files.createDirectories(OUTPUT_FILE.getParent());
        Files.writeString(OUTPUT_FILE, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(annotated));

        assertThat(annotated.key).isEqualTo("SPARK-12216");
        assertThat(annotated.fields).isNotNull();
        assertThat(annotated.fields.summary).isEqualTo("Spark failed to delete temp directory ");
        // reference-type fields flattened to their plain name
        assertThat(annotated.fields.status).isEqualTo("Resolved");
        assertThat(annotated.fields.resolution).isEqualTo("Invalid");
        assertThat(annotated.fields.priority).isEqualTo("Minor");
        assertThat(annotated.fields.project).isEqualTo("Spark");
        assertThat(annotated.fields.issuetype).isEqualTo("Bug");
        assertThat(annotated.fields.components).containsExactly("Spark Shell");
        // creator's name == key, so no "(key)" suffix
        assertThat(annotated.fields.creator).isEqualTo("skypickle");
        assertThat(annotated.fields.reporter).isEqualTo("skypickle");
        assertThat(annotated.fields.assignee).isNull();

        assertThat(annotated.fields.issuelinks).hasSize(2);
        assertThat(annotated.fields.issuelinks.get(0).inwardIssue.key).isEqualTo("SPARK-50628");

        assertThat(annotated.fields.comments).hasSize(30);

        assertThat(annotated.histories).hasSize(5);

        // customfield_XXXXX entries are carried over unchanged
        assertThat(annotated.fields.customFields).containsEntry("customfield_12310420", "9223372036854775807");

        // not sourced from the Jira server
        assertThat(annotated.annotated).isNull();
    }
}
