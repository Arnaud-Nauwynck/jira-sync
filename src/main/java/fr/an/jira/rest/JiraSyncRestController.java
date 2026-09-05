package fr.an.jira.rest;

import fr.an.jira.service.JiraSyncRunner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jira-sync")
// @Slf4j
public class JiraSyncRestController {

    private final JiraSyncRunner jiraSyncRunner;

    public JiraSyncRestController(JiraSyncRunner jiraSyncRunner) {
        this.jiraSyncRunner = jiraSyncRunner;
    }

    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        try {
            jiraSyncRunner.syncAll();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
