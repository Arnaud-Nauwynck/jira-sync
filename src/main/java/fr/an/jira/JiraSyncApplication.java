package fr.an.jira;

import fr.an.jira.configuration.JiraSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JiraSyncProperties.class)
public class JiraSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraSyncApplication.class, args);
    }
}
