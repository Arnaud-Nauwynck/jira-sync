package fr.an.jira.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "jira")
@Data
public class JiraSyncProperties {

    protected String base = "https://issues.apache.org/jira";

    protected String project = "SPARK";

    protected String out = "issues";

    protected int step = 200;

    protected long delayMs = 500;

    protected String httpHeaderAuth;

}
