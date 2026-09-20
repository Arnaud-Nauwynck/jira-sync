package fr.an.projectanalysis.claude.service.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the JSON returned by the local {@code claude} CLI ({@code --output-format json}),
 * e.g. see src/test/data/claude/claude-prompt-response.json.
 * Fields not explicitly declared are collected in each level's {@code extraFields}, so that
 * newer CLI versions adding fields do not cause parsing to lose information.
 */
@Data
public class SourceClaudeCodePromptBatchResponseDTO {

    /** the main output result of this prompt */
    public String result;


    @JsonProperty("duration_api_ms")
    public Long durationApiMs;

    @JsonProperty("stop_reason")
    public String stopReason;

    @JsonProperty("session_id")
    public String sessionId;

    @JsonProperty("total_cost_usd")
    public Double totalCostUsd;

    public SourceClaudeCodePromptUsageDTO usage;

    public Map<String, SourceClaudeCodePromptModelUsageDTO> modelUsage;

    @JsonProperty("permission_denials")
    public List<SourceClaudeCodePromptPermissionDeniedDTO> permissionDenials;

    @JsonProperty("terminal_reason")
    public String terminalReason;

    @JsonProperty("fast_mode_state")
    public String fastModeState;

    @JsonProperty("fast_mode_disabled_reason")
    public String fastModeDisabledReason;

    @JsonProperty("subagent_stats")
    public SourceClaudeCodePromptSubagentStatsDTO subagentStats;

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

    public String uuid;

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
    public static class SourceClaudeCodePromptUsageDTO {
        @JsonProperty("input_tokens")
        public Long inputTokens;

        @JsonProperty("cache_creation_input_tokens")
        public Long cacheCreationInputTokens;

        @JsonProperty("cache_read_input_tokens")
        public Long cacheReadInputTokens;

        @JsonProperty("output_tokens")
        public Long outputTokens;

        @JsonProperty("output_tokens_details")
        public SourceClaudeCodePromptOutputTokensDetailsDTO outputTokensDetails;

        @JsonProperty("server_tool_use")
        public SourceClaudeCodePromptServerToolUseDTO serverToolUse;

        @JsonProperty("service_tier")
        public String serviceTier;

        @JsonProperty("cache_creation")
        public SourceClaudeCodePromptCacheCreationDTO cacheCreation;

        @JsonProperty("inference_geo")
        public String inferenceGeo;

        public List<SourceClaudeCodePromptIterationDTO> iterations;

        public String speed;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class SourceClaudeCodePromptOutputTokensDetailsDTO {
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
    public static class SourceClaudeCodePromptServerToolUseDTO {
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
    public static class SourceClaudeCodePromptCacheCreationDTO {
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
    public static class SourceClaudeCodePromptIterationDTO {
        @JsonProperty("input_tokens")
        public Long inputTokens;

        @JsonProperty("output_tokens")
        public Long outputTokens;

        @JsonProperty("cache_read_input_tokens")
        public Long cacheReadInputTokens;

        @JsonProperty("cache_creation_input_tokens")
        public Long cacheCreationInputTokens;

        @JsonProperty("cache_creation")
        public SourceClaudeCodePromptCacheCreationDTO cacheCreation;

        public String type;

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class SourceClaudeCodePromptModelUsageDTO {
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
    public static class SourceClaudeCodePromptPermissionDeniedDTO {

        @JsonAnySetter
        public Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return new LinkedHashMap<>(extraFields);
        }
    }

    @Data
    public static class SourceClaudeCodePromptSubagentStatsDTO {
        public Integer spawned;

        public SourceClaudeCodePromptRequestedDTO requested;

        @JsonProperty("started_in_background")
        public Integer startedInBackground;

        @JsonProperty("max_depth")
        public Integer maxDepth;

        @JsonProperty("spawned_by_subagents")
        public Integer spawnedBySubagents;

        public Integer completed;

        public Integer failed;

        public SourceClaudeCodePromptKilledDTO killed;

        public SourceClaudeCodePromptRefusedDTO refused;

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
    public static class SourceClaudeCodePromptRequestedDTO {
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
    public static class SourceClaudeCodePromptKilledDTO {
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
    public static class SourceClaudeCodePromptRefusedDTO {
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
