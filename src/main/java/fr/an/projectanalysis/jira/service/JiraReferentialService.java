package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.client.JiraApiClient;
import fr.an.projectanalysis.jira.client.dtos.*;
import fr.an.projectanalysis.jira.configuration.JiraSyncProperties;
import fr.an.projectanalysis.jira.repository.JiraReferentialRepository;
import fr.an.projectanalysis.jira.rest.converter.JiraProjectComponentDTOConverter;
import fr.an.projectanalysis.jira.rest.converter.JiraProjectVersionDTOConverter;
import fr.an.projectanalysis.jira.rest.dtos.JiraAllGlobalReferentialDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectComponentDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectReferentialDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectVersionDTO;
import fr.an.projectanalysis.util.LsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service for Jira referential
 */
@Service
@Slf4j
public class JiraReferentialService {

    private final JiraSyncProperties props;

    private final ObjectMapper mapper;

    private final JiraReferentialRepository referentialRepository;

    private final JiraApiClient apiClient;

    private volatile JiraAllGlobalReferentialDTO cachedGlobalReferential;

    private volatile JiraProjectReferentialDTO cachedProjectReferential;

    private final JiraProjectVersionDTOConverter versionConverter;

    private volatile List<JiraProjectVersionDTO> cachedProjectVersions;

    private final JiraProjectComponentDTOConverter componentConverter;

    private volatile List<JiraProjectComponentDTO> cachedProjectComponents;

    public JiraReferentialService(JiraSyncProperties props, ObjectMapper mapper, JiraReferentialRepository referentialRepository, JiraApiClient apiClient,
                                  JiraProjectVersionDTOConverter versionConverter,
                                  JiraProjectComponentDTOConverter componentConverter) {
        this.props = props;
        this.mapper = mapper;
        this.referentialRepository = referentialRepository;
        this.apiClient = apiClient;
        this.versionConverter = versionConverter;
        this.componentConverter = componentConverter;
    }

    /** cached global referential: lazily loaded from the local json file, or else fetched from the remote Jira */
    public JiraAllGlobalReferentialDTO getGlobalReferential() {
        JiraAllGlobalReferentialDTO res = cachedGlobalReferential;
        if (res == null) {
            synchronized (this) {
                res = cachedGlobalReferential;
                if (res == null) {
                    res = referentialRepository.loadGlobalReferential()
                            .orElseGet(this::loadAndSaveGlobalReferential);
                    cachedGlobalReferential = res;
                }
            }
        }
        return res;
    }

    /** cached project referential: lazily loaded from the local json file, or else fetched from the remote Jira */
    public JiraProjectReferentialDTO getProjectReferential() {
        JiraProjectReferentialDTO res = cachedProjectReferential;
        if (res == null) {
            synchronized (this) {
                res = cachedProjectReferential;
                if (res == null) {
                    res = referentialRepository.loadProjectReferential(props.getProject())
                            .orElseGet(this::loadAndSaveProjectReferential);
                    cachedProjectReferential = res;
                }
            }
        }
        return res;
    }

    /** fetches all global referential data from the remote Jira, and persists it as json */
    public JiraAllGlobalReferentialDTO loadAndSaveGlobalReferential() {
        JiraAllGlobalReferentialDTO res = new JiraAllGlobalReferentialDTO();
        res.fetchedAt = Instant.now();
        res.issueTypes = apiClient.getIssueTypes();
        res.priorities = apiClient.getPriorities();
        res.resolutions = apiClient.getResolutions();
        res.statuses = apiClient.getStatuses();
        res.statusCategories = apiClient.getStatusCategories();
        res.issueLinkTypes = optionalList("issueLinkType", apiClient::getIssueLinkTypes);
        res.fields = optionalList("field", apiClient::getFields);
        res.labels = optionalList("label", apiClient::getAllLabels);
        res.projectCategories = optionalList("projectCategory", apiClient::getProjectCategories);
        referentialRepository.saveGlobalReferential(res);
        cachedGlobalReferential = res;
        return res;
    }

    /** some endpoints require authentication (HTTP 401/403 when anonymous): degrade to an empty list instead of failing */
    private <T> List<T> optionalList(String name, Supplier<List<T>> fetcher) {
        try {
            return fetcher.get();
        } catch (RuntimeException ex) {
            log.warn("skip referential '{}' (auth required? check httpHeaderAuth config): {}", name, ex.getMessage());
            return new ArrayList<>();
        }
    }

    /** fetches all referential data of the configured project from the remote Jira, and persists it as json */
    public JiraProjectReferentialDTO loadAndSaveProjectReferential() {
        JiraProjectReferentialDTO res = new JiraProjectReferentialDTO();
        res.fetchedAt = Instant.now();
        res.projectKey = props.getProject();
        res.project = apiClient.getProject();
        // redundant... project.components = apiClient.getProjectComponents();
        // redundant... project.versions = apiClient.getProjectVersions();
        res.statuses = apiClient.getProjectStatuses();
        // NOT available... res.roles = optionalList("roles", apiClient::getProjectRoles);
        // NOT available... res.assignableUsers = optionalList("assignableUsers", apiClient::getAllAssignableUsers);
        referentialRepository.saveProjectReferential(res);
        cachedProjectReferential = res;
        cachedProjectVersions = null;
        cachedProjectComponents = null;
        return res;
    }

    // getter delegate to getGlobalReferential()
    // ----------------------------------------------------------------------------------------------------------------

    public List<SourceJiraIssueTypeDefDTO> getIssueTypes() {
        return getGlobalReferential().getIssueTypes();
    }

    public List<SourceJiraPriorityDefDTO> getPriorities() {
        return getGlobalReferential().getPriorities();
    }

    public List<SourceJiraResolutionDefDTO> getResolutions() {
        return getGlobalReferential().getResolutions();
    }

    public List<SourceJiraStatusDefDTO> getStatuses() {
        return getGlobalReferential().getStatuses();
    }

    public List<SourceJiraStatusCategoryDefDTO> getStatusCategories() {
        return getGlobalReferential().getStatusCategories();
    }

    public List<SourceJiraLinkTypeDefDTO> getIssueLinkTypes() {
        return getGlobalReferential().getIssueLinkTypes();
    }

    public List<SourceJiraFieldDefDTO> getFields() {
        return getGlobalReferential().getFields();
    }

    public List<String> getLabels() {
        return getGlobalReferential().getLabels();
    }

    public List<SourceJiraProjectCategoryDefDTO> getProjectCategories() {
        return getGlobalReferential().getProjectCategories();
    }

    // getter delegate to getProjectReferential
    // ----------------------------------------------------------------------------------------------------------------

    public SourceJiraProjectDTO getProject() {
        return getProjectReferential().getProject();
    }

    // ----------------------------------------------------------------------------------------------------------------

    /** lazily converted project versions (cached until the project referential is reloaded) */
    public List<JiraProjectVersionDTO> getProjectVersions() {
        List<JiraProjectVersionDTO> res = cachedProjectVersions;
        if (res == null) {
            synchronized (this) {
                res = cachedProjectVersions;
                if (res == null) {
                    res = versionConverter.toDTOs(getProject().getVersions());
                    cachedProjectVersions = res;
                }
            }
        }
        return res;
    }

    /** lazily converted project versions (cached until the project referential is reloaded) */
    public List<JiraProjectVersionDTO> getProjectNotArchivedVersions() {
        return LsUtil.filter(getProjectVersions(), x -> x.archived == null || !x.archived);
    }

    /** lazily converted project components (cached until the project referential is reloaded) */
    public List<JiraProjectComponentDTO> getProjectComponents() {
        List<JiraProjectComponentDTO> res = cachedProjectComponents;
        if (res == null) {
            synchronized (this) {
                res = cachedProjectComponents;
                if (res == null) {
                    res = componentConverter.toDTOs(getProject().getComponents());
                    cachedProjectComponents = res;
                }
            }
        }
        return res;
    }

}
