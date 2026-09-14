package fr.an.jira.mailinglist.rest.dtos;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A single archived mailing-list message, flattened to the fields worth persisting locally.
 * Parsed from one "From "-delimited entry of a monthly mbox archive downloaded from
 * {@code https://lists.apache.org/api/mbox.lua?list={list}&domain={domain}&d={yyyy}-{mm}}
 * (see {@code src/test/data/mailing-list/archived=2026-01/dev_spark_apache_org_2026-01.mbox}
 * for a sample).
 * <p>
 * Deliberately drops everything that is internal SMTP/MTA plumbing rather than message content:
 * the mbox "From " envelope line itself, the "Received" relay hop chain, DKIM-Signature /
 * Authentication-Results / X-Google-* / X-Gm-* / X-Spam-* / X-Virus-Scanned provider-internal
 * headers, Return-Path / X-Original-To / Delivered-To, MIME-Version / Content-Type (used only
 * while parsing the body, not worth keeping), and the per-list headers that are constant across
 * every message (Mailing-List, List-ID, precedence, list-help/list-unsubscribe/list-post,
 * Reply-To) — the list/domain being synced is already known from the sync's own configuration.
 */
@Data
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

}
