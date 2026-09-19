package fr.an.projectanalysis.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Shared matching helpers for the "*Criteria" predicate classes (mirrors {@code Crit.ts} on the Angular side). */
public class CritUtils {

    private CritUtils() {
    }

    public static Pattern compilePattern(String patternText) {
        return (patternText != null && !patternText.isBlank()) ? Pattern.compile(patternText) : null;
    }

    /** Whether the (bucketed) value is in the comma-separated excluded list. */
    public static boolean isExcluded(String excludedCsv, String value) {
        return parseCsvList(excludedCsv).contains(value);
    }

    /** True when the CSV filter is blank, or at least one of the given values contains (case-insensitively) one of its comma-separated terms. */
    public static boolean matchesAny(String csvFilter, String... values) {
        List<String> terms = parseCsvList(csvFilter);
        if (terms.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String lower = value.toLowerCase();
            for (String term : terms) {
                if (lower.contains(term.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> parseCsvList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Full-match regex test; a blank pattern always matches, and a null value fails unless the pattern is blank. */
    public static boolean matchesRegex(String patternText, String value) {
        return matchesRegex(compilePattern(patternText), value);
    }

    /** Full-match test against an already-compiled pattern (see {@link #compilePattern(String)}), to
     * compile once per query instead of once per scanned record; a null pattern always matches. */
    public static boolean matchesRegex(Pattern pattern, String value) {
        return pattern == null || (value != null && pattern.matcher(value).matches());
    }

    /** Substring-search test against an already-compiled pattern (unlike {@link #matchesRegex(Pattern, String)},
     * the pattern may match anywhere in the value); a null pattern always matches. */
    public static boolean findsRegex(Pattern pattern, String value) {
        return pattern == null || (value != null && pattern.matcher(value).find());
    }

    /** 'yes' requires present, 'no' requires absent, 'any'/blank/null does not filter. */
    public static boolean matchesAvailability(String availability, boolean present) {
        if ("yes".equals(availability)) {
            return present;
        }
        if ("no".equals(availability)) {
            return !present;
        }
        return true;
    }

    public static boolean matchesDateRange(String fromDate, String toDate, LocalDateTime value) {
        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();
        if (!hasFrom && !hasTo) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (hasFrom && value.isBefore(LocalDate.parse(fromDate).atStartOfDay())) {
            return false;
        }
        if (hasTo && value.isAfter(LocalDate.parse(toDate).atTime(23, 59, 59))) {
            return false;
        }
        return true;
    }

    public static boolean matchesNumberRange(Integer min, Integer max, Integer value) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (min != null && value < min) {
            return false;
        }
        if (max != null && value > max) {
            return false;
        }
        return true;
    }

    /** min/max are expressed in kilo-tokens (thousands); value is the raw token count. */
    public static boolean matchesTokensRangeK(Integer minK, Integer maxK, int value) {
        if (minK == null && maxK == null) {
            return true;
        }
        if (minK != null && value < minK * 1000) {
            return false;
        }
        if (maxK != null && value > maxK * 1000) {
            return false;
        }
        return true;
    }

}
