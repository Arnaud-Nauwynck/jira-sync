package fr.an.projectanalysis.service;

import lombok.Getter;

/** A recorded invocation of an MCP tool, keyed by the tool name. */
@Getter
public class McpToolCallChange extends ChangeLogEvent {

    private final String toolName;
    private final String argsSummary;

    public McpToolCallChange(String toolName, String argsSummary) {
        super();
        this.toolName = toolName;
        this.argsSummary = argsSummary;
    }

    @Override
    public String summary() {
        return "MCP tool called: " + toolName + ((argsSummary != null && !argsSummary.isBlank()) ? " (" + argsSummary + ")" : "");
    }

}
