package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCriteriaDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.util.AnnotatedCritUtils;
import fr.an.projectanalysis.util.CritUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Whether a {@link MailMessageDTO} matches the Data Fetching + Main/Analysis/Development Work/
 * Personal Interest filter criteria of the mailing-list page (a null criteria matches everything).
 */
public class MailMessageCriteria implements Predicate<MailMessageDTO> {

    private final MailMessageCriteriaDTO c;

    /** The "data fetching" patterns are searched anywhere in the value, not full-matched. */
    private final Pattern fromPattern;

    private final Pattern subjectPattern;

    private final Pattern bodyPattern;

    private final Pattern messageIdPattern;

    public MailMessageCriteria(MailMessageCriteriaDTO c) {
        this.c = c;
        this.fromPattern = c != null ? CritUtils.compilePattern(c.fromPattern) : null;
        this.subjectPattern = c != null ? CritUtils.compilePattern(c.subjectPattern) : null;
        this.bodyPattern = c != null ? CritUtils.compilePattern(c.bodyPattern) : null;
        this.messageIdPattern = c != null ? CritUtils.compilePattern(c.messageIdPattern) : null;
    }

    /** Criteria filtering only on the From/Subject/body regexes (all optional, combined with AND). */
    public static MailMessageCriteria ofPatterns(String fromPatternText, String subjectPatternText, String bodyPatternText) {
        MailMessageCriteriaDTO c = new MailMessageCriteriaDTO();
        c.fromPattern = fromPatternText;
        c.subjectPattern = subjectPatternText;
        c.bodyPattern = bodyPatternText;
        return new MailMessageCriteria(c);
    }

    /** Earliest "archived" month partition to scan ('yyyy-MM', inclusive), or null when unrestricted. */
    public String getFromMonth() {
        return c != null ? c.fromMonth : null;
    }

    /** Latest "archived" month partition to scan ('yyyy-MM', inclusive), or null when unrestricted. */
    public String getToMonth() {
        return c != null ? c.toMonth : null;
    }

    @Override
    public boolean test(MailMessageDTO msg) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.findsRegex(fromPattern, msg.from)) {
            return false;
        }
        if (!CritUtils.findsRegex(subjectPattern, msg.subject)) {
            return false;
        }
        if (!CritUtils.findsRegex(bodyPattern, msg.bodyText)) {
            return false;
        }
        if (!CritUtils.findsRegex(messageIdPattern, msg.messageId)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.subjectContains, msg.subject)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.bodyContains, msg.bodyText)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.fromContains, msg.from)) {
            return false;
        }
        List<String> toCc = new ArrayList<>();
        if (msg.to != null) {
            toCc.addAll(msg.to);
        }
        if (msg.cc != null) {
            toCc.addAll(msg.cc);
        }
        if (!CritUtils.matchesAny(c.toCcContains, toCc.toArray(String[]::new))) {
            return false;
        }

        return AnnotatedCritUtils.matchesAnnotations(c, msg.annotated);
    }

}
