package fr.an.jira.repository;

import java.util.LinkedHashMap;
import java.util.Map;

public class JiraRepositoryIndexData {

    public Map<String,IssueStatPerYear> statPerYear = new LinkedHashMap<>();

    public static class IssueStatPerYear {
        public int firstKeyNum;
        public int countIssues;
    }
}
