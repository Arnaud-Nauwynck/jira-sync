package fr.an.projectanalysis.service;

import lombok.Getter;

/** A recorded change to a mailing-list message, keyed by its message-id. */
@Getter
public class MailingListChange extends ChangeLogEvent {

    private final String messageId;
    private final String subject;
    private final String changeType; // eg "create", "update"

    public MailingListChange(String messageId, String subject, String changeType) {
        super();
        this.messageId = messageId;
        this.subject = subject;
        this.changeType = changeType;
    }

    @Override
    public String summary() {
        return "Mailing-list message " + messageId + " " + changeType;
    }

}
