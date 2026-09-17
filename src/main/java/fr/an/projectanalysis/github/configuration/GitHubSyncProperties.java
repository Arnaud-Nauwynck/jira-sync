package fr.an.projectanalysis.github.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "github")
@Data
public class GitHubSyncProperties {

    protected String apiBaseUrl = "https://api.github.com";

    protected String org;

    protected String repo;

    protected String githubSyncLocalDir = "github-pulls";

    protected int perPage = 100;

    protected long syncGetByIdDelayMs = 300;
    protected long delayMs = 500;

    protected String httpHeaderAuth;

    protected List<Integer> ignorePRNumbers = new ArrayList<>();
}
