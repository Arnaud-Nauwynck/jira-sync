package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * partial info copied from SourceClaudeCodePromptBatchResponseDetailsDTO
 * see also ClaudeCodePromptBatchDTO for main (flattened infos)
 */
@Data
public class ClaudeCodePromptBatchResponseDetailsDTO {

    @JsonProperty("duration_api_ms")
    public Long durationApiMs;

    @JsonProperty("total_cost_usd")
    public Double totalCostUsd;

    public ClaudeCodePromptUsageDTO usage;

    public Map<String, ClaudeCodePromptModelUsageDTO> modelUsage;

    @JsonProperty("permission_denials")
    public List<ClaudeCodePromptPermissionDeniedDTO> permissionDenials;

    @JsonProperty("terminal_reason")
    public String terminalReason;

    @JsonProperty("fast_mode_state")
    public String fastModeState;

    @JsonProperty("fast_mode_disabled_reason")
    public String fastModeDisabledReason;

    @JsonProperty("subagent_stats")
    public ClaudeCodePromptSubagentStatsDTO subagentStats;

    @JsonProperty("is_error")
    public Boolean isError;

    @JsonProperty("num_turns")
    public Integer numTurns;

    public String subtype;

    @JsonProperty("api_error_status")
    public String apiErrorStatus;

    @JsonProperty("ttft_ms")
    public Long ttftMs;

    public String type;

    @JsonProperty("duration_ms")
    public Long durationMs;

    @JsonProperty("ttft_stream_ms")
    public Long ttftStreamMs;

    @JsonProperty("time_to_request_ms")
    public Long timeToRequestMs;

    @JsonProperty("first_content_frame_ms")
    public Long firstContentFrameMs;

    @JsonProperty("queued_turn_count")
    public Integer queuedTurnCount;

    @JsonProperty("result_index")
    public Integer resultIndex;

    /** Catch-all for any field not explicitly declared above. */
    @JsonAnySetter
    public Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return new LinkedHashMap<>(extraFields);
    }

    @Data
    public static class ClaudeCodePromptUsageDTO {
        @JsonProperty("input_tokens")
        public Long inputTokens;

        @JsonProperty("cache_creation_input_tokens")
        public Long cacheCreationInputTokens;

        @JsonProperty("cache_read_input_tokens")
        public Long cacheReadInputTokens;

        @JsonProperty("output_tokens")
        public Long outputTokens;

        @JsonProperty("output_tokens_details")
        public ClaudeCodePromptOutputTokensDetailsDTO outputTokensDetails;

        @JsonProperty("server_tool_use")
        public ClaudeCodePromptServerToolUseDTO serverToolUse;

        @JsonProperty("service_tier")
        public String serviceTier;

        @JsonProperty("cache_creation")
        public ClaudeCodePromptCacheCreationDTO cacheCreation;

        @JsonProperty("inference_geo")
        public String inferenceGeo;

        public List<ClaudeCodePromptIterationDTO> iterations;

        public String speed;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptOutputTokensDetailsDTO {
        @JsonProperty("thinking_tokens")
        public Long thinkingTokens;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptServerToolUseDTO {
        @JsonProperty("web_search_requests")
        public Integer webSearchRequests;

        @JsonProperty("web_fetch_requests")
        public Integer webFetchRequests;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptCacheCreationDTO {
        @JsonProperty("ephemeral_1h_input_tokens")
        public Long ephemeral1hInputTokens;

        @JsonProperty("ephemeral_5m_input_tokens")
        public Long ephemeral5mInputTokens;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptIterationDTO {
        @JsonProperty("input_tokens")
        public Long inputTokens;

        @JsonProperty("output_tokens")
        public Long outputTokens;

        @JsonProperty("cache_read_input_tokens")
        public Long cacheReadInputTokens;

        @JsonProperty("cache_creation_input_tokens")
        public Long cacheCreationInputTokens;

        @JsonProperty("cache_creation")
        public ClaudeCodePromptCacheCreationDTO cacheCreation;

        public String type;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptModelUsageDTO {
        public Long inputTokens;

        public Long outputTokens;

        public Long cacheReadInputTokens;

        public Long cacheCreationInputTokens;

        public Integer webSearchRequests;

        @JsonProperty("costUSD")
        public Double costUsd;

        public Long contextWindow;

        public Long maxOutputTokens;

        public Long thinkingTokens;

        public String canonicalModel;

        public String provider;

        public String costBasis;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptPermissionDeniedDTO {

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptSubagentStatsDTO {
        public Integer spawned;

        public ClaudeCodePromptRequestedDTO requested;

        @JsonProperty("started_in_background")
        public Integer startedInBackground;

        @JsonProperty("max_depth")
        public Integer maxDepth;

        @JsonProperty("spawned_by_subagents")
        public Integer spawnedBySubagents;

        public Integer completed;

        public Integer failed;

        public ClaudeCodePromptKilledDTO killed;

        public ClaudeCodePromptRefusedDTO refused;

        @JsonProperty("by_type")
        public Map<String, Object> byType;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptRequestedDTO {
        public Integer background;

        public Integer foreground;

        public Integer unset;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptKilledDTO {
        public Integer parent;

        public Integer user;

        public Integer system;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class ClaudeCodePromptRefusedDTO {
        @JsonProperty("depth_limit")
        public Integer depthLimit;

        @JsonProperty("concurrency_limit")
        public Integer concurrencyLimit;

        public Integer budget;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

}
