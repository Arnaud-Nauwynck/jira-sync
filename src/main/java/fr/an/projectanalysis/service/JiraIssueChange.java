package fr.an.projectanalysis.service;

import lombok.Getter;

/** A recorded change to a Jira issue, keyed by its issue key (eg "SPARK-1234"). */
@Getter
public class JiraIssueChange extends ChangeLogEvent {

    private final String issueKey;
    private final String changeType; // eg "create", "update", "updateAnnotation", "removeAnnotation"

    public JiraIssueChange(String issueKey, String changeType) {
        super();
        this.issueKey = issueKey;
        this.changeType = changeType;
    }

    @Override
    public String summary() {
        return "Jira issue " + issueKey + " " + changeType;
    }

}
