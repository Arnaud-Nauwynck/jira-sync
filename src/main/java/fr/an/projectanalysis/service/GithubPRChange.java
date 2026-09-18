package fr.an.projectanalysis.service;

import lombok.Getter;

/** A recorded change to a GitHub pull request, keyed by its PR number. */
@Getter
public class GithubPRChange extends ChangeLogEvent {

    private final int number;
    private final String changeType; // eg "create", "update"

    public GithubPRChange(int number, String changeType) {
        super();
        this.number = number;
        this.changeType = changeType;
    }

    @Override
    public String summary() {
        return "GitHub PR #" + number + " " + changeType;
    }

}
