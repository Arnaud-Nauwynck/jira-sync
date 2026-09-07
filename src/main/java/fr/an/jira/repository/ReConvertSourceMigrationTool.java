package fr.an.jira.repository;

import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.mapper.SourceJiraToAnnotatedIssueMapper;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import lombok.val;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Re-runs {@link SourceJiraToAnnotatedIssueMapper} over the raw-source partitions set aside by
 * {@link JiraIssueAnnotateMigration} ("issues/created_year={yyyy}/data-source.ndjson.gz", still a
 * single-entry zip container despite the ".gz" name), rewriting the annotated snapshot
 * ("data.ndjson.zip") from scratch.
 * <p>
 * Useful after a change to the mapper or to the {@link JiraIssueDTO} shape (e.g. a removed field):
 * lets the annotated snapshot be re-derived from the untouched raw source without re-fetching from
 * the Jira server. The per-issue {@link JiraIssueDTO#annotated} section (data enriched/persisted
 * locally, never coming from the source Jira server, so never filled in by the mapper) is not
 * present in the source file; it is instead read out of the previously persisted "data.ndjson.zip"
 * by key (as a raw {@link JsonNode}, not through the repository's typed read, since the "fields"
 * shape may have changed since it was written) and carried over onto the freshly re-mapped issue
 * before it is overwritten.
 * <p>
 * Run manually, e.g. {@code java -cp ... fr.an.jira.repository.ReConvertSourceMigrationTool [jiraSyncLocalDir]}
 * (defaults to "/home/arnaud/spark/spark-jira", matching {@code jira.out}/"issues"). Does not run
 * automatically at application startup.
 */
public class ReConvertSourceMigrationTool {

    private static final String SOURCE_FILE = "data-source.ndjson.gz";
    private static final String SNAPSHOT_FILE = "data.ndjson.zip";

    public static void main(String[] args) throws Exception {
        String jiraBaseDir = args.length > 0 ? args[0] : "/home/arnaud/spark/spark-jira";
        Path issuesDir = Path.of(jiraBaseDir).resolve("issues");
        ObjectMapper mapper = JsonMapper.builder().build();
        JiraIssueRepository repository = new JiraIssueRepository(issuesDir, mapper);
        new ReConvertSourceMigrationTool(repository, mapper, issuesDir).reconvertAll();
    }

    private final JiraIssueRepository repository;
    private final ObjectMapper mapper;
    private final Path issuesDir;

    public ReConvertSourceMigrationTool(JiraIssueRepository repository, ObjectMapper mapper, Path issuesDir) {
        this.repository = repository;
        this.mapper = mapper;
        this.issuesDir = issuesDir;
    }

    public static class DummyObj {
        private String field1;
    }
    public void reconvertAll() {
        val rewriteMapper = repository.getMapper();
        DummyObj obj = new DummyObj();
        String objText = rewriteMapper.writeValueAsString(obj);
        System.out.println("test json for obj with field1:null => " + objText);

        List<Integer> years = repository.findAllPartitionYears().stream()
                .filter(year -> Files.exists(sourceFile(year)))
                .collect(Collectors.toList());
        if (years.isEmpty()) {
            System.out.println("no " + SOURCE_FILE + " partitions found under " + issuesDir);
            return;
        }
        for (int year : years) {
            reconvertPartition(year);
        }
        System.out.printf("re-conversion done: %d partitions rewritten from %s%n", years.size(), SOURCE_FILE);
    }

    private void reconvertPartition(int year) {
        // folds any pending changes.ndjson into data.ndjson.zip first, so the "annotated" data we
        // are about to carry over reflects the latest recorded state, not a stale snapshot.
        // repository.compact(year);

        // Read the previously persisted snapshot as raw JsonNode rather than through the
        // repository's typed JiraIssueDTO read: the "fields" shape may have changed since it was
        // written (e.g. a field that used to hold a raw JSON object now expects a String), which
        // would make a strict typed read of the old snapshot fail. Only the "annotated" section
        // is needed here, its shape is independent of "fields" and did not change.
        Map<String, JsonNode> previousAnnotatedByKey = new LinkedHashMap<>();
        for (JsonNode rawIssue : readNdjsonZip(snapshotFile(year))) {
            JsonNode annotated = rawIssue.path("annotated");
            if (!annotated.isMissingNode() && !annotated.isNull()) {
                previousAnnotatedByKey.put(rawIssue.path("key").asText(), annotated);
            }
        }

        List<JiraIssueDTO> reconvertedIssues = new ArrayList<>();
        for (JsonNode rawIssue : readNdjsonZip(sourceFile(year))) {
            SourceJiraIssueDTO srcIssue = mapper.treeToValue(rawIssue, SourceJiraIssueDTO.class);
            JiraIssueDTO annotatedIssue = SourceJiraToAnnotatedIssueMapper.from(srcIssue);
            JsonNode previousAnnotated = previousAnnotatedByKey.get(annotatedIssue.key);
            if (previousAnnotated != null) {
                annotatedIssue.annotated = mapper.treeToValue(previousAnnotated, JiraIssueDTO.IssueExtraFieldsDTO.class);
            }
            reconvertedIssues.add(annotatedIssue);
        }

        repository.importSnapshot(year, reconvertedIssues);
        System.out.println("re-converted " + reconvertedIssues.size() + " issues in "
                + JiraIssueRepository.partitionDirName(year));
    }

    /** Reads a single-entry ndjson zip file (one JSON object per line), or an empty list if absent. */
    private List<JsonNode> readNdjsonZip(Path zipFile) {
        List<JsonNode> result = new ArrayList<>();
        if (!Files.exists(zipFile)) {
            return result;
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return result;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                result.add(mapper.readTree(line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + zipFile, e);
        }
        return result;
    }

    private Path sourceFile(int year) {
        return issuesDir.resolve(JiraIssueRepository.partitionDirName(year)).resolve(SOURCE_FILE);
    }

    private Path snapshotFile(int year) {
        return issuesDir.resolve(JiraIssueRepository.partitionDirName(year)).resolve(SNAPSHOT_FILE);
    }
}
