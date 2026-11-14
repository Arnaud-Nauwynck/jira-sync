package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.IssuesCountsDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.util.LsUtil;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.val;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builder to count issues by issueTypes x status x priority x resolution x projectComponents */
public class IssuesCountsBuilder {

    protected final Map<IssueKey,IssuesCountByKeyBuilder> countByKeys = new LinkedHashMap<>();
    private int total;
    private int totalLive;


    @Value
    @AllArgsConstructor
    public static class IssueKey {
        public final String type;
        public final String status;
        public final String priority;
        public final String resolution;
        public final String component;
    }

    @AllArgsConstructor
    protected static class IssuesCountByKeyBuilder {
        final IssueKey key;
        int count;

        public IssuesCountsDTO.IssuesCountByKeyDTO buildEntryDTO() {
            Object[] keyValues = new Object[] { key.type, key.status, key.priority, key.resolution, key.component };
            return new IssuesCountsDTO.IssuesCountByKeyDTO(keyValues, count);
        }
    }

    public IssuesCountsDTO build() {
        val countByKeyDTOs = LsUtil.map(countByKeys.values(), IssuesCountByKeyBuilder::buildEntryDTO);
        return new IssuesCountsDTO(total, totalLive, countByKeyDTOs);
    }

    public void accept(JiraIssueDTO issue) {
        this.total++;
        if (issue.fields == null) {
            return; // should not occur
        }
        val fields = issue.fields;
        val issueStatus = fields.status;
        if (issueStatus != null && ("Closed".equalsIgnoreCase(issueStatus) || "Resolved".equalsIgnoreCase(issueStatus))) {
            return;
        }
        this.totalLive++;
        val issueComponents = fields.components;

        // an issue with several components is counted once per component
        List<String> components = (issueComponents == null || issueComponents.isEmpty())
                ? Collections.singletonList(null) : issueComponents;
        for (String component : components) {
            val key = new IssueKey(fields.issuetype, issueStatus, fields.priority, fields.resolution, component);
            countByKeys.computeIfAbsent(key, k -> new IssuesCountByKeyBuilder(k, 0)).count++;
        }
    }


}
