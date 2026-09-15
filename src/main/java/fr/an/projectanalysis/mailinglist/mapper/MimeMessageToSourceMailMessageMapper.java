package fr.an.projectanalysis.mailinglist.mapper;

import fr.an.projectanalysis.mailinglist.client.dtos.SourceMailMessageDTO;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.stream.Field;

import java.io.IOException;
import java.io.Reader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts a parsed {@link Message} (see {@code org.apache.james.mime4j.dom}) into the flattened
 * {@link SourceMailMessageDTO}, keeping only the fields worth persisting (see that class's javadoc).
 */
public class MimeMessageToSourceMailMessageMapper {

    private static final Pattern MESSAGE_ID_TOKEN = Pattern.compile("<[^<>]+>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private MimeMessageToSourceMailMessageMapper() {
    }

    public static SourceMailMessageDTO from(Message src) {
        if (src == null) {
            return null;
        }
        SourceMailMessageDTO dest = new SourceMailMessageDTO();
        dest.messageId = stripAngleBrackets(src.getMessageId());
        dest.inReplyTo = stripAngleBrackets(headerBody(src, "In-Reply-To"));
        dest.references = parseMessageIdList(headerBody(src, "References"));
        dest.subject = src.getSubject();
        dest.from = src.getFrom() != null ? formatMailboxes(src.getFrom()) : null;
        dest.to = src.getTo() != null
                ? src.getTo().stream().map(MimeMessageToSourceMailMessageMapper::formatAddress).collect(Collectors.toList())
                : List.of();
        dest.cc = src.getCc() != null
                ? src.getCc().stream().map(MimeMessageToSourceMailMessageMapper::formatAddress).collect(Collectors.toList())
                : List.of();
        dest.date = parseDate(headerBody(src, "Date"), src.getDate());
        dest.bodyText = extractBodyText(src);
        return dest;
    }

    private static String headerBody(Message src, String fieldName) {
        if (src.getHeader() == null) {
            return null;
        }
        Field field = src.getHeader().getField(fieldName);
        return field != null ? field.getBody() : null;
    }

    private static String stripAngleBrackets(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> parseMessageIdList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        Matcher m = MESSAGE_ID_TOKEN.matcher(raw);
        while (m.find()) {
            String token = m.group();
            ids.add(token.substring(1, token.length() - 1));
        }
        return ids;
    }

    private static String formatMailboxes(List<Mailbox> mailboxes) {
        if (mailboxes.isEmpty()) {
            return null;
        }
        return mailboxes.stream().map(MimeMessageToSourceMailMessageMapper::formatMailbox)
                .collect(Collectors.joining(", "));
    }

    private static String formatAddress(Address address) {
        return (address instanceof Mailbox mailbox) ? formatMailbox(mailbox) : address.toString();
    }

    private static String formatMailbox(Mailbox mailbox) {
        String name = mailbox.getName();
        return (name != null && !name.isBlank()) ? name + " <" + mailbox.getAddress() + ">" : mailbox.getAddress();
    }

    /** Prefers parsing the raw Date header (keeps its original UTC offset); falls back to mime4j's own parsed Date. */
    private static OffsetDateTime parseDate(String rawDateHeader, java.util.Date fallback) {
        if (rawDateHeader != null && !rawDateHeader.isBlank()) {
            try {
                return OffsetDateTime.parse(rawDateHeader.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            } catch (Exception ignored) {
                // fall through to the fallback below
            }
        }
        return fallback != null ? OffsetDateTime.ofInstant(fallback.toInstant(), ZoneOffset.UTC) : null;
    }

    /** Prefers the text/plain part of a multipart/alternative body; falls back to a tag-stripped text/html part. */
    private static String extractBodyText(Entity entity) {
        String[] plainAndHtml = collectPlainAndHtml(entity);
        if (plainAndHtml[0] != null) {
            return plainAndHtml[0];
        }
        return plainAndHtml[1] != null ? stripHtml(plainAndHtml[1]) : null;
    }

    /** Returns {plainText, htmlText}, either possibly null; the first non-null part of each type found wins. */
    private static String[] collectPlainAndHtml(Entity entity) {
        String plain = null;
        String html = null;
        if (entity.isMultipart() && entity.getBody() instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                String[] nested = collectPlainAndHtml(part);
                if (plain == null) plain = nested[0];
                if (html == null) html = nested[1];
            }
        } else if (entity.getBody() instanceof TextBody textBody) {
            String text = readAll(textBody);
            String mimeType = entity.getMimeType();
            if (mimeType != null && mimeType.equalsIgnoreCase("text/html")) {
                html = text;
            } else {
                plain = text;
            }
        }
        return new String[]{plain, html};
    }

    private static String readAll(TextBody textBody) {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = textBody.getReader()) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }

    /** Minimal best-effort HTML-to-text fallback, used only when no text/plain part is present. */
    private static String stripHtml(String html) {
        String withoutTags = HTML_TAG.matcher(html).replaceAll(" ");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
