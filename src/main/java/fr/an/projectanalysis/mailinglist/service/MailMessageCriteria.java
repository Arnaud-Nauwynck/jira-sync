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

    public MailMessageCriteria(MailMessageCriteriaDTO c) {
        this.c = c;
        this.fromPattern = c != null ? CritUtils.compilePattern(c.fromPattern) : null;
        this.subjectPattern = c != null ? CritUtils.compilePattern(c.subjectPattern) : null;
        this.bodyPattern = c != null ? CritUtils.compilePattern(c.bodyPattern) : null;
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
