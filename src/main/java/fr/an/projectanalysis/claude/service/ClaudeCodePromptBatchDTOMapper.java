package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchResponseDetailsDTO;
import fr.an.projectanalysis.claude.service.dto.SourceClaudeCodePromptBatchResponseDTO;
import lombok.val;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClaudeCodePromptBatchDTOMapper {

    /** extract flatten main infos from SourceClaudeCodePromptBatchResponseDTO src, to ClaudeCodePromptBatchDTO res */
    public static ClaudeCodePromptBatchDTO toDTO(
            long startTime,
            String prompt,
            double endTime,
            SourceClaudeCodePromptBatchResponseDTO src
    ) {
        val res = new ClaudeCodePromptBatchDTO();
        res.startTime = startTime;
        res.prompt = prompt;
        res.elapsedSeconds = (endTime - startTime) / 1000.0;
        res.outputResult = src.result;
        res.totalCostUsd = src.totalCostUsd != null ? src.totalCostUsd : 0.0;
        SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptUsageDTO usage = src.usage;
        if (usage != null) {
            res.inputTokens = usage.inputTokens;
            res.cacheCreationInputTokens = usage.cacheCreationInputTokens;
            res.cacheReadInputTokens = usage.cacheReadInputTokens;
            res.outputTokens = usage.outputTokens;
            if (usage.outputTokensDetails != null) {
                res.outputThinkingTokens = usage.outputTokensDetails.thinkingTokens;
            }
        }

        res.details = extractRemainDetailsDTO(src);
        return res;
    }

    /** extract details infos (not already in extractMainInfos), and not skipped,
     * from SourceClaudeCodePromptBatchResponseDTO src, to ClaudeCodePromptBatchResponseDetailsDTO res
     */
    private static ClaudeCodePromptBatchResponseDetailsDTO extractRemainDetailsDTO(SourceClaudeCodePromptBatchResponseDTO src) {
        ClaudeCodePromptBatchResponseDetailsDTO res = new ClaudeCodePromptBatchResponseDetailsDTO();
        res.durationApiMs = src.durationApiMs;
        res.totalCostUsd = src.totalCostUsd;
        res.usage = toUsageDTO(src.usage);
        res.modelUsage = toModelUsageMap(src.modelUsage);
        res.permissionDenials = toPermissionDenialsList(src.permissionDenials);
        res.terminalReason = src.terminalReason;
        res.fastModeState = src.fastModeState;
        res.fastModeDisabledReason = src.fastModeDisabledReason;
        res.subagentStats = toSubagentStatsDTO(src.subagentStats);
        res.isError = src.isError;
        res.numTurns = src.numTurns;
        res.subtype = src.subtype;
        res.apiErrorStatus = src.apiErrorStatus;
        res.ttftMs = src.ttftMs;
        res.type = src.type;
        res.durationMs = src.durationMs;
        res.ttftStreamMs = src.ttftStreamMs;
        res.timeToRequestMs = src.timeToRequestMs;
        res.firstContentFrameMs = src.firstContentFrameMs;
        res.queuedTurnCount = src.queuedTurnCount;
        res.resultIndex = src.resultIndex;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptUsageDTO toUsageDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptUsageDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptUsageDTO res = new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptUsageDTO();
        res.inputTokens = src.inputTokens;
        res.cacheCreationInputTokens = src.cacheCreationInputTokens;
        res.cacheReadInputTokens = src.cacheReadInputTokens;
        res.outputTokens = src.outputTokens;
        res.outputTokensDetails = toOutputTokensDetailsDTO(src.outputTokensDetails);
        res.serverToolUse = toServerToolUseDTO(src.serverToolUse);
        res.serviceTier = src.serviceTier;
        res.cacheCreation = toCacheCreationDTO(src.cacheCreation);
        res.inferenceGeo = src.inferenceGeo;
        res.iterations = toIterationsList(src.iterations);
        res.speed = src.speed;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptOutputTokensDetailsDTO toOutputTokensDetailsDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptOutputTokensDetailsDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptOutputTokensDetailsDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptOutputTokensDetailsDTO();
        res.thinkingTokens = src.thinkingTokens;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptServerToolUseDTO toServerToolUseDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptServerToolUseDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptServerToolUseDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptServerToolUseDTO();
        res.webSearchRequests = src.webSearchRequests;
        res.webFetchRequests = src.webFetchRequests;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptCacheCreationDTO toCacheCreationDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptCacheCreationDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptCacheCreationDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptCacheCreationDTO();
        res.ephemeral1hInputTokens = src.ephemeral1hInputTokens;
        res.ephemeral5mInputTokens = src.ephemeral5mInputTokens;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static List<ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptIterationDTO> toIterationsList(
            List<SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptIterationDTO> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(ClaudeCodePromptBatchDTOMapper::toIterationDTO).collect(Collectors.toList());
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptIterationDTO toIterationDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptIterationDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptIterationDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptIterationDTO();
        res.inputTokens = src.inputTokens;
        res.outputTokens = src.outputTokens;
        res.cacheReadInputTokens = src.cacheReadInputTokens;
        res.cacheCreationInputTokens = src.cacheCreationInputTokens;
        res.cacheCreation = toCacheCreationDTO(src.cacheCreation);
        res.type = src.type;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static Map<String, ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptModelUsageDTO> toModelUsageMap(
            Map<String, SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptModelUsageDTO> src) {
        if (src == null) {
            return null;
        }
        Map<String, ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptModelUsageDTO> res = new LinkedHashMap<>();
        for (Map.Entry<String, SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptModelUsageDTO> entry : src.entrySet()) {
            res.put(entry.getKey(), toModelUsageDTO(entry.getValue()));
        }
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptModelUsageDTO toModelUsageDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptModelUsageDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptModelUsageDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptModelUsageDTO();
        res.inputTokens = src.inputTokens;
        res.outputTokens = src.outputTokens;
        res.cacheReadInputTokens = src.cacheReadInputTokens;
        res.cacheCreationInputTokens = src.cacheCreationInputTokens;
        res.webSearchRequests = src.webSearchRequests;
        res.costUsd = src.costUsd;
        res.contextWindow = src.contextWindow;
        res.maxOutputTokens = src.maxOutputTokens;
        res.thinkingTokens = src.thinkingTokens;
        res.canonicalModel = src.canonicalModel;
        res.provider = src.provider;
        res.costBasis = src.costBasis;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static List<ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptPermissionDeniedDTO> toPermissionDenialsList(
            List<SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptPermissionDeniedDTO> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(ClaudeCodePromptBatchDTOMapper::toPermissionDeniedDTO).collect(Collectors.toList());
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptPermissionDeniedDTO toPermissionDeniedDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptPermissionDeniedDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptPermissionDeniedDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptPermissionDeniedDTO();
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptSubagentStatsDTO toSubagentStatsDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptSubagentStatsDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptSubagentStatsDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptSubagentStatsDTO();
        res.spawned = src.spawned;
        res.requested = toRequestedDTO(src.requested);
        res.startedInBackground = src.startedInBackground;
        res.maxDepth = src.maxDepth;
        res.spawnedBySubagents = src.spawnedBySubagents;
        res.completed = src.completed;
        res.failed = src.failed;
        res.killed = toKilledDTO(src.killed);
        res.refused = toRefusedDTO(src.refused);
        res.byType = src.byType;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRequestedDTO toRequestedDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptRequestedDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRequestedDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRequestedDTO();
        res.background = src.background;
        res.foreground = src.foreground;
        res.unset = src.unset;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptKilledDTO toKilledDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptKilledDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptKilledDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptKilledDTO();
        res.parent = src.parent;
        res.user = src.user;
        res.system = src.system;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

    private static ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRefusedDTO toRefusedDTO(
            SourceClaudeCodePromptBatchResponseDTO.SourceClaudeCodePromptRefusedDTO src) {
        if (src == null) {
            return null;
        }
        ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRefusedDTO res =
                new ClaudeCodePromptBatchResponseDetailsDTO.ClaudeCodePromptRefusedDTO();
        res.depthLimit = src.depthLimit;
        res.concurrencyLimit = src.concurrencyLimit;
        res.budget = src.budget;
        res.extraFields.putAll(src.extraFields);
        return res;
    }

}
