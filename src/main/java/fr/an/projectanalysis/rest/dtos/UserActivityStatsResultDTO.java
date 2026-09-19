package fr.an.projectanalysis.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user activity stats for the 3 independent sources (Jira, GitHub, mailing-list), each keyed
 * by that source's own user identity (Jira reporter, GitHub login, mail "From" address) - these
 * identities are not cross-mapped to one another, so results are kept as 3 separate maps rather
 * than merged into a single per-user row.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserActivityStatsResultDTO {

    public Map<String, JiraUserActivityStatsDTO> jira = new LinkedHashMap<>();
    public Map<String, GithubUserActivityStatsDTO> github = new LinkedHashMap<>();
    public Map<String, MailMessageUserActivityStatsDTO> mail = new LinkedHashMap<>();

}
