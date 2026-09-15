package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Mirrors the JSON returned by the GitHub REST API "GET /rate_limit" endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GitHubRateLimitDTO {

    public Resources resources;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Resources {
        public RateLimit core;
        public RateLimit graphql;
        public RateLimit search;
        public RateLimit code_search;
        public RateLimit source_import;
        public RateLimit integration_manifest;
        public RateLimit actions_runner_registration;
        public RateLimit scim;
        public RateLimit dependency_snapshots;
        public RateLimit dependency_sbom;
        public RateLimit code_scanning_autofix;
        public RateLimit copilot_usage_records;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class RateLimit {
        public int limit;
        public int remaining;
        public long reset;
        public int used;
    }

}
