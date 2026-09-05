package fr.an.jira.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jira")
@Data
public class JiraSyncProperties {

    protected String jiraApiServerBaseUrl = "https://issues.apache.org/jira";

    protected String project = "SPARK";

    protected String jiraSyncLocalDir = "issues";

    protected int maxResults = 200;

    protected long delayMs = 500;

    protected String httpHeaderAuth;

}
