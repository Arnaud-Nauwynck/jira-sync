package fr.an.projectanalysis.configuration;

import fr.an.projectanalysis.github.mcp.GitHubPullRequestMcpTools;
import fr.an.projectanalysis.jira.mcp.JiraMcpTools;
import fr.an.projectanalysis.mailinglist.mcp.MailingListMcpTools;
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

    @Bean
    public ToolCallbackProvider gitHubPrMcpToolCallbackProvider(GitHubPullRequestMcpTools gitHubPrMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(gitHubPrMcpTools)
                .build();
    }

    @Bean
    public ToolCallbackProvider mailingListMcpToolCallbackProvider(MailingListMcpTools mailingListMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mailingListMcpTools)
                .build();
    }
}
