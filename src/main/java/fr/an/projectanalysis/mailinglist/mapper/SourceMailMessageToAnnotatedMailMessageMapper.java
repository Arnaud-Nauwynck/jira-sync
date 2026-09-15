package fr.an.projectanalysis.mailinglist.mapper;

import fr.an.projectanalysis.mailinglist.client.dtos.SourceMailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;

/**
 * Converts the raw {@link SourceMailMessageDTO} (mirroring the parsed mbox archive message) into
 * the {@link MailMessageDTO} hierarchy.
 * Does not set {@link MailMessageDTO#annotated}, which carries data enriched/persisted locally
 * rather than coming from the source mailing-list archive.
 */
public class SourceMailMessageToAnnotatedMailMessageMapper {

    private SourceMailMessageToAnnotatedMailMessageMapper() {
    }

    public static MailMessageDTO from(SourceMailMessageDTO src) {
        if (src == null) {
            return null;
        }
        MailMessageDTO dest = new MailMessageDTO();
        dest.messageId = src.messageId;
        dest.inReplyTo = src.inReplyTo;
        dest.references = src.references;
        dest.subject = src.subject;
        dest.from = src.from;
        dest.to = src.to;
        dest.cc = src.cc;
        dest.date = src.date;
        dest.bodyText = src.bodyText;
        return dest;
    }
}
