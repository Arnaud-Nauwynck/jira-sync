package fr.an.jira.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "jira")
public record JiraSyncProperties(
        @DefaultValue("https://issues.apache.org/jira") String base,
        @DefaultValue("SPARK") String project,
        @DefaultValue("issues") Path out,
        @DefaultValue("200") int step,
        @DefaultValue("500") long delayMs,
        String pat) {
}
