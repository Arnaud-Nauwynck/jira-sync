package fr.an.jira.repository;

import fr.an.jira.client.dtos.JiraIssueDTO;
import fr.an.jira.mapper.JiraToAnnotatedIssueMapper;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-off migration of the previously persisted raw-{@code JiraIssueDTO}-shaped partitions
 * ("issues/created_year={yyyy}/data.ndjson.zip") to the flattened {@link AnnotatedJiraIssueDTO}
 * shape now written by {@link JiraIssueRepository#save}.
 * <p>
 * For each partition: folds any pending {@code changes.ndjson} into the snapshot, renames the
 * existing (source-shaped) "data.ndjson.zip" to "data-source.ndjson.gz" (kept, not deleted, still
 * in its original zip container despite the ".gz" name), then writes a fresh "data.ndjson.zip"
 * with the mapped, annotated-shaped issues ({@code annotated} left null: there is no pre-existing
 * annotated data to carry over on a first migration).
 * <p>
 * Run manually, e.g. {@code java -cp ... fr.an.jira.repository.JiraIssueAnnotateMigration [jiraSyncLocalDir]}
 * (defaults to "/home/arnaud/spark/spark-jira", matching {@code jira.out}/"issues"). Does not run
 * automatically at application startup.
 */
public class JiraIssueAnnotateMigration {

    private static final String OLD_SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String RENAMED_SOURCE_FILE = "data-source.ndjson.gz";

    public static void main(String[] args) throws Exception {
        String jiraBaseDir = args.length > 0 ? args[0] : "/home/arnaud/spark/spark-jira";
        Path issuesDir = Path.of(jiraBaseDir).resolve("issues");
        ObjectMapper mapper = JsonMapper.builder().build();
        JiraIssueRepository repository = new JiraIssueRepository(issuesDir, mapper);
        new JiraIssueAnnotateMigration(repository, mapper, issuesDir).migrate();
    }

    private final JiraIssueRepository repository;
    private final ObjectMapper mapper;
    private final Path issuesDir;

    public JiraIssueAnnotateMigration(JiraIssueRepository repository, ObjectMapper mapper, Path issuesDir) {
        this.repository = repository;
        this.mapper = mapper;
        this.issuesDir = issuesDir;
    }

    public void migrate() {
        List<Integer> years = repository.findAllPartitionYears();
        if (years.isEmpty()) {
            System.out.println("no partitions found under " + issuesDir);
            return;
        }
        for (int year : years) {
            migratePartition(year);
        }
        System.out.printf("migration done: %d partitions converted to the annotated format%n", years.size());
    }

    private void migratePartition(int year) {
        // folds any pending changes.ndjson into data.ndjson.zip and clears it, so the source-shaped
        // snapshot we are about to rename away holds the up-to-date, complete partition state.
        repository.compact(year);

        List<JsonNode> rawIssues = new ArrayList<>();
        repository.scanIssues(year, year, (y, issue) -> rawIssues.add(issue));

        List<JsonNode> annotatedIssues = new ArrayList<>();
        for (JsonNode rawIssue : rawIssues) {
            JiraIssueDTO srcIssue = mapper.treeToValue(rawIssue, JiraIssueDTO.class);
            AnnotatedJiraIssueDTO annotatedIssue = JiraToAnnotatedIssueMapper.from(srcIssue);
            annotatedIssues.add(mapper.valueToTree(annotatedIssue));
        }

        Path partitionDir = issuesDir.resolve(JiraIssueRepository.partitionDirName(year));
        Path oldSnapshotFile = partitionDir.resolve(OLD_SNAPSHOT_FILE);
        Path renamedSourceFile = partitionDir.resolve(RENAMED_SOURCE_FILE);
        try {
            Files.move(oldSnapshotFile, renamedSourceFile);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rename " + oldSnapshotFile + " to " + renamedSourceFile, e);
        }

        repository.importSnapshot(year, annotatedIssues);
        System.out.println("migrated " + annotatedIssues.size() + " issues in "
                + JiraIssueRepository.partitionDirName(year));
    }
}
