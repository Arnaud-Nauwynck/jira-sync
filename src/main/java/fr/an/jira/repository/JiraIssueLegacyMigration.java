package fr.an.jira.repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One-off migration from the legacy layout (one flat "issues/{KEY}.json" file per issue) to the
 * partitioned layout used by {@link JiraIssueRepository} ("issues/created_year={yyyy}/data.ndjson.zip").
 * <p>
 * Run manually, e.g. {@code java -cp ... fr.an.jira.repository.JiraIssueLegacyMigration [outDir]}
 * (outDir defaults to "issues", matching {@code jira.out}). Does not run automatically at
 * application startup.
 */
public class JiraIssueLegacyMigration {

    public static void main(String[] args) throws Exception {
        String jiraBaseDir = args.length > 0 ? args[0] : "/home/arnaud/spark/spark-jira";
        Path issuesDir = Path.of(jiraBaseDir).resolve("issues");
        Path flatInputIssuesDir = Path.of(jiraBaseDir).resolve("issues-flat");
        ObjectMapper mapper = JsonMapper.builder().build();
        JiraIssueRepository repository = new JiraIssueRepository(issuesDir, mapper);
        new JiraIssueLegacyMigration(repository, mapper, flatInputIssuesDir).migrate();
    }

    private final JiraIssueRepository repository;
    private final ObjectMapper mapper;
    private final Path flatInputIssuesDir;

    public JiraIssueLegacyMigration(JiraIssueRepository repository, ObjectMapper mapper, Path issuesDir) {
        this.repository = repository;
        this.mapper = mapper;
        this.flatInputIssuesDir = issuesDir;
    }

    /** Reads every legacy flat "{KEY}.json" file, groups by partition, writes a compacted snapshot per partition. */
    public void migrate() {
        List<Path> legacyFiles = listLegacyFiles();
        if (legacyFiles.isEmpty()) {
            System.out.println("no legacy flat issue files found under " + flatInputIssuesDir);
            return;
        }

        Map<Integer, List<JsonNode>> byPartitionYear = new TreeMap<>();
        for (Path file : legacyFiles) {
            JsonNode issue = mapper.readTree(file.toFile());
            byPartitionYear.computeIfAbsent(JiraIssueRepository.partitionYearOf(issue), y -> new ArrayList<>()).add(issue);
        }

        for (Map.Entry<Integer, List<JsonNode>> entry : byPartitionYear.entrySet()) {
            repository.importSnapshot(entry.getKey(), entry.getValue());
            System.out.println("migrated " + entry.getValue().size() + " issues into "
                    + JiraIssueRepository.partitionDirName(entry.getKey()));
        }

        boolean debugDelete = false;
        if (debugDelete) {
            for (Path file : legacyFiles) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    throw new UncheckedIOException("failed to delete legacy file " + file, e);
                }
            }
        }
        System.out.printf("migration done: %d legacy files -> %d partitions%n",
                legacyFiles.size(), byPartitionYear.size());
    }

    private List<Path> listLegacyFiles() {
        if (!Files.exists(flatInputIssuesDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(flatInputIssuesDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + flatInputIssuesDir, e);
        }
    }
}
