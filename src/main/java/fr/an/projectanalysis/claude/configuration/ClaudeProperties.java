package fr.an.projectanalysis.claude.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "claude")
@Data
public class ClaudeProperties {

    private String workingDir;

}
