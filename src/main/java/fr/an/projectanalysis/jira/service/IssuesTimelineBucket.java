package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.IssuesTimelineBucketBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Calendar-aligned (UTC) time bucket: weeks start on Monday, 3-months buckets are calendar quarters. */
public class IssuesTimelineBucket {

    private final IssuesTimelineBucketBy bucketBy;

    public IssuesTimelineBucket(IssuesTimelineBucketBy bucketBy) {
        this.bucketBy = (bucketBy != null) ? bucketBy : IssuesTimelineBucketBy.ONE_DAY;
    }

    /** First day of the bucket containing the given day. */
    public LocalDate startOf(LocalDate day) {
        switch (bucketBy) {
            case ONE_WEEK:
                return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case ONE_MONTH:
                return day.withDayOfMonth(1);
            case THREE_MONTHS:
                return LocalDate.of(day.getYear(), day.getMonthValue() - (day.getMonthValue() - 1) % 3, 1);
            case ONE_DAY:
            default:
                return day;
        }
    }

    /** First day of the bucket following the one starting at the given bucket start. */
    public LocalDate next(LocalDate bucketStart) {
        switch (bucketBy) {
            case ONE_WEEK:
                return bucketStart.plusWeeks(1);
            case ONE_MONTH:
                return bucketStart.plusMonths(1);
            case THREE_MONTHS:
                return bucketStart.plusMonths(3);
            case ONE_DAY:
            default:
                return bucketStart.plusDays(1);
        }
    }

}
