package fr.an.projectanalysis.jira.client;

import fr.an.projectanalysis.jira.client.dtos.*;
import fr.an.projectanalysis.jira.configuration.JiraSyncProperties;
import fr.an.projectanalysis.util.HttpApiClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delegates all HTTP calls to the remote Jira Server/DC REST API.
 * - 429 / 5xx backoff with retry
 * - adds the configured Authorization header, if any
 */
@Component
@Slf4j
public class JiraApiClient {

    private static final int PAGE_SIZE = 50;

    private final JiraSyncProperties props;

    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public JiraApiClient(JiraSyncProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------------------------------------
    // project referentials
    // ------------------------------------------------------------------------------------------------

    /** GET /rest/api/2/project/{key} : project details (lead, category, components, issue types, versions, roles) */
    public SourceJiraProjectDTO getProject() {
        return getObject(projectPath(""), SourceJiraProjectDTO.class);
    }

    /** GET /rest/api/2/project/{key}/components */
    public List<SourceJiraProjectComponentDTO> getProjectComponents() {
        return getList(projectPath("/components"), SourceJiraProjectComponentDTO.class);
    }

    /** GET /rest/api/2/project/{key}/versions */
    public List<SourceJiraProjectVersionDTO> getProjectVersions() {
        return getList(projectPath("/versions"), SourceJiraProjectVersionDTO.class);
    }

    /** GET /rest/api/2/project/{key}/statuses : allowed statuses, per issue type */
    public List<SourceJiraProjectStatusesDTO> getProjectStatuses() {
        return getList(projectPath("/statuses"), SourceJiraProjectStatusesDTO.class);
    }

    /** GET /rest/api/2/project/{key}/role : map role name -> role url */
    public Map<String, String> getProjectRoleUrls() {
        return mapper.convertValue(callHttpGet(projectPath("/role")),
                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
    }

    /** GET /rest/api/2/project/{key}/role/{roleId} : role with its actors (users / groups) */
    public SourceJiraProjectRoleDTO getProjectRole(long roleId) {
        return getObject(projectPath("/role/" + roleId), SourceJiraProjectRoleDTO.class);
    }

    /** all roles of the project, with their actors: one GET per role listed by {@link #getProjectRoleUrls()} */
    public List<SourceJiraProjectRoleDTO> getProjectRoles() {
        List<SourceJiraProjectRoleDTO> roles = new ArrayList<>();
        for (String roleUrl : getProjectRoleUrls().values()) {
            long roleId = Long.parseLong(roleUrl.substring(roleUrl.lastIndexOf('/') + 1));
            roles.add(getProjectRole(roleId));
        }
        return roles;
    }

    /** GET /rest/api/2/user/assignable/search?project={key} */
    public List<SourceJiraUserDefDTO> getAssignableUsers(int startAt, int maxResults) {
        return getList("/rest/api/2/user/assignable/search?project=" + encode(props.getProject())
                + "&startAt=" + startAt + "&maxResults=" + maxResults, SourceJiraUserDefDTO.class);
    }

    // ------------------------------------------------------------------------------------------------
    // global referentials
    // ------------------------------------------------------------------------------------------------

    /** GET /rest/api/2/issuetype */
    public List<SourceJiraIssueTypeDefDTO> getIssueTypes() {
        return getList("/rest/api/2/issuetype", SourceJiraIssueTypeDefDTO.class);
    }

    /** GET /rest/api/2/priority */
    public List<SourceJiraPriorityDefDTO> getPriorities() {
        return getList("/rest/api/2/priority", SourceJiraPriorityDefDTO.class);
    }

    /** GET /rest/api/2/resolution */
    public List<SourceJiraResolutionDefDTO> getResolutions() {
        return getList("/rest/api/2/resolution", SourceJiraResolutionDefDTO.class);
    }

    /** GET /rest/api/2/status */
    public List<SourceJiraStatusDefDTO> getStatuses() {
        return getList("/rest/api/2/status", SourceJiraStatusDefDTO.class);
    }

    /** GET /rest/api/2/statuscategory */
    public List<SourceJiraStatusCategoryDefDTO> getStatusCategories() {
        return getList("/rest/api/2/statuscategory", SourceJiraStatusCategoryDefDTO.class);
    }

    /** GET /rest/api/2/issueLinkType */
    public List<SourceJiraLinkTypeDefDTO> getIssueLinkTypes() {
        return getObject("/rest/api/2/issueLinkType", SourceJiraLinkTypesResponseDTO.class).issueLinkTypes;
    }

    /** GET /rest/api/2/field : system + custom fields definitions */
    public List<SourceJiraFieldDefDTO> getFields() {
        return getList("/rest/api/2/field", SourceJiraFieldDefDTO.class);
    }

    /** GET /rest/api/2/label (paginated, labels are free text) */
    public SourceJiraPageDTO<String> getLabels(int startAt, int maxResults) {
        return getPage("/rest/api/2/label?startAt=" + startAt + "&maxResults=" + maxResults, String.class);
    }

    /** GET /rest/api/2/projectCategory */
    public List<SourceJiraProjectCategoryDefDTO> getProjectCategories() {
        return getList("/rest/api/2/projectCategory", SourceJiraProjectCategoryDefDTO.class);
    }

    /** GET /rest/api/2/user/search?username={query} ("." matches all users on Jira Server) */
    public List<SourceJiraUserDefDTO> searchUsers(String query, int startAt, int maxResults) {
        return getList("/rest/api/2/user/search?username=" + encode(query)
                + "&startAt=" + startAt + "&maxResults=" + maxResults, SourceJiraUserDefDTO.class);
    }

    // ------------------------------------------------------------------------------------------------
    // agile (boards, sprints, epics)
    // ------------------------------------------------------------------------------------------------

    /** GET /rest/agile/1.0/board?projectKeyOrId={key} */
    public SourceJiraPageDTO<SourceJiraAgileBoardDTO> getBoards(int startAt, int maxResults) {
        return getPage("/rest/agile/1.0/board?projectKeyOrId=" + encode(props.getProject())
                + "&startAt=" + startAt + "&maxResults=" + maxResults, SourceJiraAgileBoardDTO.class);
    }

    /** GET /rest/agile/1.0/board/{boardId}/sprint */
    public SourceJiraPageDTO<SourceJiraAgileSprintDTO> getBoardSprints(long boardId, int startAt, int maxResults) {
        return getPage("/rest/agile/1.0/board/" + boardId + "/sprint?startAt=" + startAt
                + "&maxResults=" + maxResults, SourceJiraAgileSprintDTO.class);
    }

    /** GET /rest/agile/1.0/board/{boardId}/epic */
    public SourceJiraPageDTO<SourceJiraAgileEpicDTO> getBoardEpics(long boardId, int startAt, int maxResults) {
        return getPage("/rest/agile/1.0/board/" + boardId + "/epic?startAt=" + startAt
                + "&maxResults=" + maxResults, SourceJiraAgileEpicDTO.class);
    }

    // ------------------------------------------------------------------------------------------------
    // "get all pages" variants of the paginated endpoints
    // ------------------------------------------------------------------------------------------------

    public List<String> getAllLabels() {
        return fetchAllPages(this::getLabels);
    }

    public List<SourceJiraAgileBoardDTO> getAllBoards() {
        return fetchAllPages(this::getBoards);
    }

    public List<SourceJiraAgileSprintDTO> getAllBoardSprints(long boardId) {
        return fetchAllPages((startAt, max) -> getBoardSprints(boardId, startAt, max));
    }

    public List<SourceJiraAgileEpicDTO> getAllBoardEpics(long boardId) {
        return fetchAllPages((startAt, max) -> getBoardEpics(boardId, startAt, max));
    }

    public List<SourceJiraUserDefDTO> getAllAssignableUsers() {
        return fetchAllUsers(this::getAssignableUsers);
    }

    public List<SourceJiraUserDefDTO> searchAllUsers(String query) {
        return fetchAllUsers((startAt, max) -> searchUsers(query, startAt, max));
    }

    @FunctionalInterface
    private interface PageFetcher<T> {
        SourceJiraPageDTO<T> fetch(int startAt, int maxResults);
    }

    @FunctionalInterface
    private interface ListFetcher<T> {
        List<T> fetch(int startAt, int maxResults);
    }

    /** loops over pages until the response says it is the last one (or is empty) */
    private <T> List<T> fetchAllPages(PageFetcher<T> fetcher) {
        List<T> res = new ArrayList<>();
        for (int startAt = 0; ; ) {
            SourceJiraPageDTO<T> page = fetcher.fetch(startAt, PAGE_SIZE);
            if (page == null || page.values == null || page.values.isEmpty()) break;
            res.addAll(page.values);
            if (Boolean.TRUE.equals(page.isLast)) break;
            startAt += page.values.size();
            if (page.total != null && startAt >= page.total) break;
        }
        return res;
    }

    /** loops over non-wrapped list responses (no paging info) until a page is empty or not full */
    private <T> List<T> fetchAllUsers(ListFetcher<T> fetcher) {
        List<T> res = new ArrayList<>();
        for (int startAt = 0; ; ) {
            List<T> page = fetcher.fetch(startAt, PAGE_SIZE);
            if (page == null || page.isEmpty()) break;
            res.addAll(page);
            if (page.size() < PAGE_SIZE) break;
            startAt += page.size();
        }
        return res;
    }

    // ------------------------------------------------------------------------------------------------
    // typed GET helpers
    // ------------------------------------------------------------------------------------------------

    private String projectPath(String suffix) {
        return "/rest/api/2/project/" + encode(props.getProject()) + suffix;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private <T> T getObject(String pathAndQuery, Class<T> type) {
        return mapper.convertValue(callHttpGet(pathAndQuery), type);
    }

    private <T> List<T> getList(String pathAndQuery, Class<T> elementType) {
        return mapper.convertValue(callHttpGet(pathAndQuery),
                mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private <T> SourceJiraPageDTO<T> getPage(String pathAndQuery, Class<T> elementType) {
        return mapper.convertValue(callHttpGet(pathAndQuery),
                mapper.getTypeFactory().constructParametricType(SourceJiraPageDTO.class, elementType));
    }

    /** GETs the given path+query (relative to the configured Jira base URL) and parses the JSON response. */
    public JsonNode callHttpGet(String pathAndQuery) {
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.getJiraApiServerBaseUrl() + pathAndQuery))
                    .timeout(Duration.ofMinutes(2))
                    .header("Accept", "application/json")
                    .GET();
            String httpHeaderAuth = props.getHttpHeaderAuth();
            if (httpHeaderAuth != null && !httpHeaderAuth.isBlank()) {
                rb.header("Authorization", httpHeaderAuth);
            }

            HttpRequest httpReq = rb.build();
            HttpResponse<String> resp = null;
            try {
                resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            int sc = resp.statusCode();
            if (sc == 200) {
                return mapper.readTree(resp.body());
            } else {
                if ((sc == 429 || sc >= 500) && attempt <= 5) {
                    long backoff = HttpApiClientUtils.retryAfterMs(resp, attempt);
                    log.warn("HTTP {} on {}, retry {} in {}ms", sc, pathAndQuery, attempt, backoff);
                    HttpApiClientUtils.sleep(backoff);
                    continue;
                }
                throw new RuntimeException("HTTP " + sc + " on " + pathAndQuery + " : "
                        + resp.body().substring(0, Math.min(500, resp.body().length())));
            }
        }
    }

}
