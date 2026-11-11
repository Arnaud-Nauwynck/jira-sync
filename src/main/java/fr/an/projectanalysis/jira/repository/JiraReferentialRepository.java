package fr.an.projectanalysis.jira.repository;

import fr.an.projectanalysis.jira.configuration.JiraSyncProperties;
import fr.an.projectanalysis.jira.rest.dtos.JiraAllGlobalReferentialDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectReferentialDTO;
import fr.an.projectanalysis.service.RecentChangeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@Slf4j
public class JiraReferentialRepository {

    private final JiraSyncProperties props;
    private final ObjectMapper mapper;
    private final RecentChangeLogService changeLogService;
    private final Path baseReferentialDir;

    public JiraReferentialRepository(JiraSyncProperties props, ObjectMapper mapper, RecentChangeLogService changeLogService) {
        this.baseReferentialDir = Path.of(props.getJiraSyncLocalDir()).resolve("referential");
        this.props = props;
        this.mapper = mapper;
        this.changeLogService = changeLogService;
    }

    private Path globalReferentialFile() {
        return baseReferentialDir.resolve("global-referential.json");
    }

    public void saveGlobalReferential(JiraAllGlobalReferentialDTO referential) {
        Path file = globalReferentialFile();
        try {
            Files.createDirectories(baseReferentialDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), referential);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write " + file, ex);
        }
        log.info("saved global referential to {}", file);
    }

    public Optional<JiraAllGlobalReferentialDTO> loadGlobalReferential() {
        Path file = globalReferentialFile();
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(mapper.readValue(file.toFile(), JiraAllGlobalReferentialDTO.class));
    }

    private Path projectReferentialFile(String projectKey) {
        return baseReferentialDir.resolve("project-" + projectKey + "-referential.json");
    }

    public void saveProjectReferential(JiraProjectReferentialDTO referential) {
        Path file = projectReferentialFile(referential.projectKey);
        try {
            Files.createDirectories(baseReferentialDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), referential);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write " + file, ex);
        }
        log.info("saved project referential to {}", file);
    }

    public Optional<JiraProjectReferentialDTO> loadProjectReferential(String projectKey) {
        Path file = projectReferentialFile(projectKey);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(mapper.readValue(file.toFile(), JiraProjectReferentialDTO.class));
    }

}
