package fr.an.projectanalysis.mailinglist.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a raw UNIX mbox byte stream (as downloaded from lists.apache.org) into the individual
 * raw RFC 822 messages it contains, each delimited by a "From " envelope line, e.g.
 * {@code From dev-return-39391-...@spark.apache.org  Thu Jan  1 00:01:48 2026}.
 */
public class MboxMessageSplitter {

    /**
     * Matches the mbox "From " envelope separator line. Anchored on the trailing asctime-style
     * date ("Thu Jan  1 00:01:48 2026") rather than just "^From ", to avoid false positives on
     * body text that happens to start a line with "From " (e.g. a quoted "From: ..." header
     * reformatted by some mail client).
     */
    private static final Pattern FROM_LINE = Pattern.compile(
            "(?m)^From \\S+\\s+[A-Za-z]{3}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+\\d{4}$");

    private MboxMessageSplitter() {
    }

    /** Splits the given mbox content into its individual raw (still MIME-encoded) messages. */
    public static List<byte[]> splitMessages(byte[] mboxBytes) {
        List<byte[]> result = new ArrayList<>();
        if (mboxBytes.length == 0) {
            return result;
        }
        // mbox "From " lines are plain US-ASCII; ISO-8859-1 maps bytes 1:1 to chars, so the char
        // offsets found here are also valid byte offsets into the original array (the actual
        // message bytes, in whatever charset each MIME part declares, are never touched here).
        String asLatin1 = new String(mboxBytes, StandardCharsets.ISO_8859_1);
        Matcher m = FROM_LINE.matcher(asLatin1);
        List<Integer> messageStarts = new ArrayList<>();
        while (m.find()) {
            messageStarts.add(skipLineTerminator(asLatin1, m.end()));
        }
        for (int i = 0; i < messageStarts.size(); i++) {
            int from = messageStarts.get(i);
            int to = (i + 1 < messageStarts.size()) ? messageStarts.get(i + 1) : mboxBytes.length;
            if (to > from) {
                result.add(Arrays.copyOfRange(mboxBytes, from, to));
            }
        }
        return result;
    }

    private static int skipLineTerminator(String text, int pos) {
        if (pos < text.length() && text.charAt(pos) == '\r') pos++;
        if (pos < text.length() && text.charAt(pos) == '\n') pos++;
        return pos;
    }
}
