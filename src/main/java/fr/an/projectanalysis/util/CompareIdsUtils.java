package fr.an.projectanalysis.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares 2 lists of ids (as returned by the "query-ids" endpoints of the jira / github / mailing-list
 * services): the ids matched by the left query only, by both ("common"), and by the right query only.
 */
public class CompareIdsUtils {

    /** Result of {@link #compareIds}; the per-module result DTOs are filled from it. */
    public static class CompareIdsResult<T> {
        /** Ids present on the left side only, in left list order. */
        public final List<T> leftOnlyIds = new ArrayList<>();
        /** Ids present on both sides, in left list order; null when not requested. */
        public final List<T> commonIds;
        /** Count of ids present on both sides, always filled (even when {@link #commonIds} is null). */
        public int commonCount;
        /** Ids present on the right side only, in right list order. */
        public final List<T> rightOnlyIds = new ArrayList<>();

        private CompareIdsResult(boolean fillCommonIds) {
            this.commonIds = fillCommonIds ? new ArrayList<>() : null;
        }
    }

    /** Splits {@code leftIds} and {@code rightIds} into left-only, common and right-only ids, preserving
     * the order of each input list. The common ids are only counted, unless {@code fillCommonIds} is set. */
    public static <T> CompareIdsResult<T> compareIds(List<T> leftIds, List<T> rightIds, boolean fillCommonIds) {
        CompareIdsResult<T> res = new CompareIdsResult<>(fillCommonIds);
        Set<T> leftIdSet = new HashSet<>(leftIds);
        Set<T> rightIdSet = new HashSet<>(rightIds);
        for (T id : leftIds) {
            if (rightIdSet.contains(id)) {
                res.commonCount++;
                if (res.commonIds != null) {
                    res.commonIds.add(id);
                }
            } else {
                res.leftOnlyIds.add(id);
            }
        }
        for (T id : rightIds) {
            if (!leftIdSet.contains(id)) {
                res.rightOnlyIds.add(id);
            }
        }
        return res;
    }

}
