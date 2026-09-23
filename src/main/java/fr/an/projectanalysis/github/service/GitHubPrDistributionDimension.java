package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;

import java.util.List;

/** Supported grouping dimensions for {@code GitHubPullRequestService#queryDistributionStats}. */
public enum GitHubPrDistributionDimension {

    STATE {
        @Override
        public List<String> valuesOf(GitHubPullRequestDTO pr) {
            String state = pr.merged ? "merged" : pr.state;
            return List.of(state != null ? state : "unknown");
        }
    },
    AUTHOR {
        @Override
        public List<String> valuesOf(GitHubPullRequestDTO pr) {
            return List.of(GitHubPrCriteria.authorOf(pr));
        }
    },
    LABEL {
        @Override
        public List<String> valuesOf(GitHubPullRequestDTO pr) {
            return (pr.labelNames == null || pr.labelNames.isEmpty()) ? List.of("(no label)") : pr.labelNames;
        }
    },
    BASE_REF {
        @Override
        public List<String> valuesOf(GitHubPullRequestDTO pr) {
            return List.of(pr.baseRef != null ? pr.baseRef : "unknown");
        }
    },
    MERGEABLE_STATE {
        @Override
        public List<String> valuesOf(GitHubPullRequestDTO pr) {
            return List.of(pr.mergeableState != null ? pr.mergeableState : "unknown");
        }
    };

    /** The bucket name(s) the given PR contributes to (more than one for multi-valued dimensions like LABEL). */
    public abstract List<String> valuesOf(GitHubPullRequestDTO pr);

}
