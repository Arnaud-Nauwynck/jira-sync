package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.IssuesTimelineDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesTimelineDTO.ComponentTimelineDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesTimelineDTO.IssueTypeSeriesDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesTimelineQueryDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.util.DateTimeUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Accumulates the issues creation counts per time bucket, issue type and component, into an {@link IssuesTimelineDTO}. */
public class IssuesTimelineBuilder {

    public static final String NO_COMPONENT = "(no component)";

    private final IssuesTimelineBucket bucket;
    private final boolean countOpenedBefore;
    private final boolean creationOnly;
    private final Set<String> excludedComponents;
    private final Set<String> excludedIssueTypes;

    private final LocalDate firstBucketStart;
    private final LocalDate endDate;
    private final List<LocalDate> bucketStarts = new ArrayList<>();
    private final Map<LocalDate, Integer> bucketIndexByStart = new HashMap<>();

    /** Creation and resolution counts of 1 issue type; each array has 1 slot per bucket, plus a last slot for the issues before. */
    private static class TypeCounts {
        final int[] created;
        final int[] resolved;

        TypeCounts(int slotCount) {
            this.created = new int[slotCount];
            this.resolved = new int[slotCount];
        }
    }

    private final Map<String, Map<String, TypeCounts>> countsByComponent = new TreeMap<>();
    private final Map<String, Integer> issueCountByComponent = new HashMap<>();

    public IssuesTimelineBuilder(IssuesTimelineQueryDTO query) {
        this.bucket = new IssuesTimelineBucket(query.bucketBy);
        this.countOpenedBefore = query.countOpenedBefore;
        this.creationOnly = query.creationOnly == null || query.creationOnly;
        this.excludedComponents = toSet(query.excludedComponents);
        this.excludedIssueTypes = toSet(query.excludedIssueTypes);
        this.endDate = LocalDate.parse(query.endDate);
        this.firstBucketStart = bucket.startOf(LocalDate.parse(query.startDate));
        for (LocalDate start = firstBucketStart; !start.isAfter(endDate); start = bucket.next(start)) {
            bucketIndexByStart.put(start, bucketStarts.size());
            bucketStarts.add(start);
        }
    }

    /** First year ("created_year" partition) to scan. */
    public static int fromYearOf(IssuesTimelineQueryDTO query, int oldestYear) {
        return query.countOpenedBefore ? oldestYear : LocalDate.parse(query.startDate).getYear();
    }

    public static int toYearOf(IssuesTimelineQueryDTO query) {
        return LocalDate.parse(query.endDate).getYear();
    }

    public void accept(JiraIssueDTO issue) {
        if (issue.fields == null) {
            return;
        }
        long createdMillis = DateTimeUtils.toEpochMillisOr0(issue.fields.created);
        String issueType = issue.fields.issuetype != null ? issue.fields.issuetype : "";
        if (createdMillis == 0L || excludedIssueTypes.contains(issueType)) {
            return;
        }
        LocalDate created = Instant.ofEpochMilli(createdMillis).atZone(ZoneOffset.UTC).toLocalDate();
        int slot = slotOf(bucket.startOf(created), created);
        if (slot < 0) {
            return;
        }
        int resolvedSlot = resolvedSlotOf(issue.fields.resolutiondate);
        List<String> components = (issue.fields.components != null && !issue.fields.components.isEmpty())
                ? issue.fields.components : List.of(NO_COMPONENT);
        for (String component : new HashSet<>(components)) {
            if (excludedComponents.contains(component)) {
                continue;
            }
            TypeCounts counts = countsByComponent.computeIfAbsent(component, k -> new TreeMap<>())
                    .computeIfAbsent(issueType, k -> new TypeCounts(bucketStarts.size() + 1));
            counts.created[slot]++;
            if (resolvedSlot >= 0) {
                counts.resolved[resolvedSlot]++;
            }
            issueCountByComponent.merge(component, 1, Integer::sum);
        }
    }

    /** Slot of the counts array for an issue resolved at the given time, or -1 when not resolved, or after the end date. */
    private int resolvedSlotOf(String resolutionDateTime) {
        long resolvedMillis = DateTimeUtils.toEpochMillisOr0(resolutionDateTime);
        if (resolvedMillis == 0L) {
            return -1;
        }
        LocalDate resolved = Instant.ofEpochMilli(resolvedMillis).atZone(ZoneOffset.UTC).toLocalDate();
        return slotOf(bucket.startOf(resolved), resolved);
    }

    /** Slot of the counts array for an issue created in the given bucket: the last one for "before", or -1 when not counted. */
    private int slotOf(LocalDate createdBucketStart, LocalDate created) {
        if (created.isAfter(endDate)) {
            return -1;
        }
        if (createdBucketStart.isBefore(firstBucketStart)) {
            return countOpenedBefore ? bucketStarts.size() : -1;
        }
        return bucketIndexByStart.get(createdBucketStart);
    }

    public IssuesTimelineDTO build() {
        IssuesTimelineDTO res = new IssuesTimelineDTO();
        res.bucketStarts = new ArrayList<>();
        for (LocalDate start : bucketStarts) {
            res.bucketStarts.add(start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        res.components = new ArrayList<>();
        countsByComponent.forEach((component, countsByType) -> {
            ComponentTimelineDTO c = new ComponentTimelineDTO();
            c.component = component;
            c.issueCount = issueCountByComponent.get(component);
            c.series = new ArrayList<>();
            countsByType.forEach((issueType, counts) -> c.series.add(toSeries(issueType, counts)));
            res.components.add(c);
        });
        return res;
    }

    private IssueTypeSeriesDTO toSeries(String issueType, TypeCounts counts) {
        IssueTypeSeriesDTO s = new IssueTypeSeriesDTO();
        s.issueType = issueType;
        s.counts = new ArrayList<>(bucketStarts.size());
        int last = bucketStarts.size(); // slot of the issues created/resolved before the first bucket
        int totalCreated = counts.created[last]; // 0 unless countOpenedBefore
        int totalResolved = counts.resolved[last];
        for (int i = 0; i < last; i++) {
            totalCreated += counts.created[i];
            totalResolved += counts.resolved[i];
            if (!creationOnly) {
                s.counts.add(totalCreated - totalResolved);
            } else {
                s.counts.add(countOpenedBefore ? totalCreated : counts.created[i]);
            }
        }
        return s;
    }

    private static Set<String> toSet(List<String> values) {
        return values != null ? new HashSet<>(values) : Set.of();
    }

}
