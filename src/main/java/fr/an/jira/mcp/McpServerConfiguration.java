package fr.an.jira.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

    @Bean
    public ToolCallbackProvider jiraMcpToolCallbackProvider(JiraMcpTools jiraMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(jiraMcpTools)
                .build();
    }
}
