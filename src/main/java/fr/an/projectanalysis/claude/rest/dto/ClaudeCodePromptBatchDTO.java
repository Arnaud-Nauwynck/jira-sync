package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A finished {@code claude} CLI prompt invocation: start info ({@link #prompt}, {@link #startTime})
 * plus end info ({@link #output}, {@link #elapsedSeconds}), persisted by
 * {@code fr.an.projectanalysis.claude.repository.ClaudeCodePromptBatchRepository}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor @AllArgsConstructor
public class ClaudeCodePromptBatchDTO {

    public long startTime;

    public String prompt;

    public double elapsedSeconds;

    /** json output ... cf parse as SourceClaudeCodePromptBatchResponseDTO */
    @Deprecated
    @JsonIgnore
    public String output;

    /** main text part of the process output, extracted from json 'output.result' */
    public String outputResult;

    public ClaudeCodePromptBatchResponseDetailsDTO details;

    @JsonProperty("total_cost_usd") // from output '.total_cost_usd'
    public Double totalCostUsd;

    // from output 'usage.input_tokens'
    @JsonProperty("input_tokens")
    public Long inputTokens;

    // from output 'usage.cache_creation_input_tokens'
    @JsonProperty("cache_creation_input_tokens")
    public Long cacheCreationInputTokens;

    // from output 'usage.cache_read_input_tokens'
    @JsonProperty("cache_read_input_tokens")
    public Long cacheReadInputTokens;

    // from output 'usage.output_tokens'
    @JsonProperty("output_tokens")
    public Long outputTokens;

    // from output 'usage.output_tokens_details.thinking_tokens'
    @JsonProperty("output_thinking_tokens")
    public Long outputThinkingTokens;


}
