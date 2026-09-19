package fr.an.projectanalysis.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/**
 * Parses the date-time texts kept as plain strings in the locally mirrored source json, whose offset
 * is written either with a ':' separator ("2024-12-19T22:25:12.000+00:00"), without it (Jira style:
 * "2024-12-19T22:25:12.000+0000"), or as "Z".
 */
public class DateTimeUtils {

    /** ISO local date-time, followed by an offset written with or without a ':' separator, or as "Z". */
    private static final DateTimeFormatter ISO_LENIENT_OFFSET_FMT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter();

    private DateTimeUtils() {
    }

    /** Epoch milliseconds of the given date-time text, or 0 when it is null, blank, or not parseable. */
    public static long toEpochMillisOr0(String dateTimeText) {
        if (dateTimeText == null || dateTimeText.isBlank()) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(dateTimeText.trim(), ISO_LENIENT_OFFSET_FMT).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return 0L;
        }
    }

    /** "yyyy-MM" calendar month of the given date-time text, or null when it is null, blank, or not parseable. */
    public static String monthOf(String dateTimeText) {
        if (dateTimeText == null || dateTimeText.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime dt = OffsetDateTime.parse(dateTimeText.trim(), ISO_LENIENT_OFFSET_FMT);
            return String.format("%04d-%02d", dt.getYear(), dt.getMonthValue());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

}
