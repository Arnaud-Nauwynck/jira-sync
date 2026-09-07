package fr.an.jira.repository;

import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.mapper.SourceJiraToAnnotatedIssueMapper;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * One-off migration of the previously persisted raw-{@code JiraIssueDTO}-shaped partitions
 * ("issues/created_year={yyyy}/data.ndjson.zip") to the flattened {@link JiraIssueDTO}
 * shape now written by {@link JiraIssueRepository#save}.
 * <p>
 * For each partition: reads the still-raw-shaped "data.ndjson.zip" and folds any pending
 * "changes.ndjson" on top of it (both read generically as {@link JsonNode}, since the repository
 * itself only ever reads/writes the flattened {@link JiraIssueDTO} shape and cannot be used for
 * this one-off bridge), renames "data.ndjson.zip" to "data-source.ndjson.gz" (kept, not deleted,
 * still in its original zip container despite the ".gz" name), then writes a fresh
 * "data.ndjson.zip" with the mapped, annotated-shaped issues via {@link JiraIssueRepository#importSnapshot}
 * ({@code annotated} left null: there is no pre-existing annotated data to carry over on a first
 * migration).
 * <p>
 * Run manually, e.g. {@code java -cp ... fr.an.jira.repository.JiraIssueAnnotateMigration [jiraSyncLocalDir]}
 * (defaults to "/home/arnaud/spark/spark-jira", matching {@code jira.out}/"issues"). Does not run
 * automatically at application startup.
 */
public class JiraIssueAnnotateMigration {

    private static final String OLD_SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String OLD_CHANGES_FILE = "changes.ndjson";
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
        Path partitionDir = issuesDir.resolve(JiraIssueRepository.partitionDirName(year));
        Path oldSnapshotFile = partitionDir.resolve(OLD_SNAPSHOT_FILE);
        Path oldChangesFile = partitionDir.resolve(OLD_CHANGES_FILE);
        Path renamedSourceFile = partitionDir.resolve(RENAMED_SOURCE_FILE);

        // folds any pending changes.ndjson into the still-raw-shaped snapshot, so the source-shaped
        // snapshot we are about to rename away holds the up-to-date, complete partition state.
        Map<String, JsonNode> rawByKey = new LinkedHashMap<>();
        readOldSnapshot(oldSnapshotFile, rawByKey);
        replayOldChanges(oldChangesFile, rawByKey);

        List<JiraIssueDTO> annotatedIssues = new ArrayList<>();
        for (JsonNode rawIssue : rawByKey.values()) {
            SourceJiraIssueDTO srcIssue = mapper.treeToValue(rawIssue, SourceJiraIssueDTO.class);
            annotatedIssues.add(SourceJiraToAnnotatedIssueMapper.from(srcIssue));
        }

        try {
            Files.move(oldSnapshotFile, renamedSourceFile);
            Files.deleteIfExists(oldChangesFile);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rename " + oldSnapshotFile + " to " + renamedSourceFile, e);
        }

        repository.importSnapshot(year, annotatedIssues);
        System.out.println("migrated " + annotatedIssues.size() + " issues in "
                + JiraIssueRepository.partitionDirName(year));
    }

    /** Reads a single-entry ndjson zip file (one raw-shaped JSON object per line), keyed by "key". */
    private void readOldSnapshot(Path zipFile, Map<String, JsonNode> target) {
        if (!Files.exists(zipFile)) {
            return;
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode issue = mapper.readTree(line);
                target.put(issue.path("key").asText(), issue);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + zipFile, e);
        }
    }

    /** Replays a raw-shaped "changes.ndjson" log (as recorded by the pre-refactor repository) onto {@code target}. */
    private void replayOldChanges(Path file, Map<String, JsonNode> target) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonNode rec = mapper.readTree(line);
                String change = rec.path("change").asText();
                switch (change) {
                    case "insert", "update" -> {
                        JsonNode data = rec.path("data");
                        target.put(data.path("key").asText(), data);
                    }
                    case "delete" -> target.remove(rec.path("key").asText());
                    default -> throw new IllegalStateException("unknown change type '" + change + "' in " + file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read changes log " + file, e);
        }
    }
}
