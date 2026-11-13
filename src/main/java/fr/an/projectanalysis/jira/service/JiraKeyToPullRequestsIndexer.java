package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds an index "Jira key" -> numbers of GitHub PRs mentioning it in their title, branch name or body. */
@Service
public class JiraKeyToPullRequestsIndexer {

    private static final Pattern JIRA_KEY = Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");

    private final GitHubPullRequestRepository prRepository;

    public JiraKeyToPullRequestsIndexer(GitHubPullRequestRepository prRepository) {
        this.prRepository = prRepository;
    }

    public Map<String, List<Integer>> buildIndex() {
        Map<String, List<Integer>> res = new LinkedHashMap<>();
        prRepository.scanPullRequests(Integer.MIN_VALUE, Integer.MAX_VALUE, (year, pr) -> {
            String text = pr.title + "\n" + pr.headRef + "\n" + pr.body;
            Matcher m = JIRA_KEY.matcher(text);
            while (m.find()) {
                List<Integer> numbers = res.computeIfAbsent(m.group(), k -> new ArrayList<>());
                if (!numbers.contains(pr.number)) {
                    numbers.add(pr.number);
                }
            }
        });
        return res;
    }

}
