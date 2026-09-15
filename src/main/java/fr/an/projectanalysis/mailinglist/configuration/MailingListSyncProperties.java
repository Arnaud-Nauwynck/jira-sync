package fr.an.projectanalysis.mailinglist.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mailinglist")
@Data
public class MailingListSyncProperties {

    protected String apiBaseUrl = "https://lists.apache.org";

    protected String list = "dev";

    protected String domain = "spark.apache.org";

    protected String mailingListSyncLocalDir = "mailing-list";

    /** Earliest month ("yyyy-MM") to fetch when nothing has been synced locally yet. */
    protected String startYearMonth = "2023-01";

    protected long delayMs = 500;

    protected String httpHeaderAuth;

}
