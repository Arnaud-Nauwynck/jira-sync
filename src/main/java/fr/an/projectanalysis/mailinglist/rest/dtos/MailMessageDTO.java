package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Flattened / converted copy of {@code fr.an.jira.mailinglist.client.dtos.SourceMailMessageDTO}:
 * - carries the same message fields as the source, unchanged.
 * - an {@link #annotated} section is added to carry extra data that does not come from the
 *   source mailing-list archive, and is not filled in by the mapping from
 *   {@code SourceMailMessageDTO}.
 * See {@code fr.an.jira.mailinglist.mapper.SourceMailMessageToAnnotatedMailMessageMapper} for the
 * conversion from {@code SourceMailMessageDTO}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageDTO {

    /** {@code Message-ID} header; globally unique, used as the local dedup/primary key. */
    public String messageId;

    /** {@code In-Reply-To} header; the immediate parent message's Message-ID, or null for a thread root. */
    public String inReplyTo;

    /** {@code References} header, split into individual Message-IDs; full thread ancestry, root first. */
    public List<String> references;

    /** {@code Subject} header, as-is (still carries any leading "Re: " / "[VOTE]" / "[SPIP]" tag). */
    public String subject;

    /** {@code From} header: sender display name + email, e.g. "Hyukjin Kwon &lt;gurwls223@apache.org&gt;". */
    public String from;

    /** {@code To} header recipients (for a list post, usually just the list address itself). */
    public List<String> to;

    /** {@code Cc} header recipients, if any. */
    public List<String> cc;

    /**
     * {@code Date} header, parsed to an absolute instant. Deliberately not the mbox "From " line's
     * date: that one is the relaying server's receive time (in server-local time, no explicit
     * offset), not the author's send time.
     */
    public OffsetDateTime date;

    /**
     * The message body as plain text: the {@code text/plain} MIME part when the message is
     * {@code multipart/alternative}, decoded from its Content-Transfer-Encoding (quoted-printable /
     * base64) and declared charset; falls back to a tags-stripped {@code text/html} part when no
     * {@code text/plain} part is present.
     */
    public String bodyText;

    /** Extra data not coming from the source mailing-list archive; not set by the mapper. */
    public MailMessageExtraFieldsDTO annotated;

    public MailMessageExtraFieldsDTO annotatedOrCreate() {
        if (annotated == null) {
            annotated = new MailMessageExtraFieldsDTO();
        }
        return annotated;
    }

    /**
     * Display id combining {@link #messageId} with the "yyyy-MM-dd" prefix of {@link #date} when
     * known, e.g. {@code "2024-03-15-<abc123@apache.org>"}: more readable / sortable than the raw
     * Message-ID, and the same "{yyyy-MM-dd}-{messageId}" shape that
     * {@code MailMessageRepository#findByMessageId} recognizes to infer the partition month and
     * avoid scanning every partition. Falls back to the plain {@link #messageId} when the date is
     * unknown.
     */
    public String id() {
        return (date != null) ? date.toLocalDate() + "-" + messageId : messageId;
    }

}
