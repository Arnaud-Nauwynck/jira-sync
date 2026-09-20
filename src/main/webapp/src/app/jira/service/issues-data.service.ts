import { Injectable, computed, signal } from '@angular/core';
import { Observable, map, of, switchMap, tap } from 'rxjs';
import { IssueIdAndLastUpdateTimeDTO, IssuesCriteriaDTO, IssuesPartitionStatsDTO, JiraIssueDTO, JiraIssuesService, NearbyJiraIssuesDTO } from '../../rest';
import { mergeDeltaIds } from '../../utils/criteria-delta';
import { EntityCache } from '../../utils/entity-cache';
import { parseEpochMillis } from '../../utils/epoch-millis';
import { sameCriteria } from '../../utils/same-criteria';
import { compareIssueKeysDesc } from './issue-key-order';
import { IssueCriteria } from './IssueCriteria';

const DEFAULT_LIMIT = 5000;

@Injectable({ providedIn: 'root' })
export class IssuesDataService {

  // Search criteria (and fetch limit) of the issues search page, edited in-place by the criteria view.
  // Held here, in this root-scoped service, so they are not lost when navigating away from and back to the page.
  readonly criteria: IssuesCriteriaDTO = {
    fromYear: 2020,
    toYear: 2050,
    pullRequestAvailableLabel: 'any',
    analysisAvailability: 'any',
    developmentWorkAvailability: 'any',
    personalInterrestAvailability: 'any',
    excludedStatuses: 'Resolved,Closed',
  };

  limit = DEFAULT_LIMIT;

  // Every issue fetched from the server so far, so that changing the criteria only downloads the
  // issues newly matched, and not the ones already held (see query()).
  private readonly cache = new EntityCache<string, JiraIssueDTO>((issue) => issue.key!);

  // Keys of the issues matching the last searched criteria, ordered by issue number descending.
  private readonly resultIds = signal<string[]>([]);

  // Row Data: the issues of the last search, shared with anyone injecting this service.
  readonly issues = computed<JiraIssueDTO[]>(() => {
    this.cache.version(); // re-evaluate whenever the cached issues change, and not only the result keys
    return this.cache.getAll(this.resultIds());
  });

  // Snapshot (deep copy) of the criteria, and the limit, that produced the current `issues` result list:
  // a copy, because `criteria` is a mutable object that the criteria view keeps editing in place.
  readonly lastCriteria = signal<IssuesCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced issues per "created_year" partition, loaded once and used to show how many
  // issues are available versus how many currently match the search criteria.
  readonly partitionStats = signal<IssuesPartitionStatsDTO | undefined>(undefined);

  constructor(private jiraIssuesService: JiraIssuesService) {}

  /** True when `issues` already holds the result of exactly this criteria+limit, so re-querying is useless. */
  isUpToDate(criteria: IssuesCriteriaDTO, limit: number): boolean {
    const lastCriteria = this.lastCriteria();
    return lastCriteria !== undefined && limit === this.lastLimit() && sameCriteria(lastCriteria, criteria);
  }

  /**
   * Queries the issues matching the criteria: the returned (cold) Observable performs the query on
   * subscription, and updates the cached `issues` signal with the result.
   *
   * Only what is not held yet is downloaded, which of the 3 query modes applies depending on how the
   * criteria compares with the one that produced the currently held result:
   * - no result held yet: the full result is downloaded (see {@link queryFull});
   * - the very same criteria+limit, re-searched: only the matching keys and their last update time
   *   are downloaded (see {@link queryRefreshOutdated});
   * - any other criteria: only its difference with the held result is downloaded (see {@link queryByDelta}).
   */
  query(criteria: IssuesCriteriaDTO, limit = DEFAULT_LIMIT): Observable<JiraIssueDTO[]> {
    const previousCriteria = this.lastCriteria();
    if (previousCriteria === undefined) {
      return this.queryFull(criteria, limit);
    }
    return this.isUpToDate(criteria, limit)
        ? this.queryRefreshOutdated(criteria, limit)
        : this.queryByDelta(criteria, limit, previousCriteria, this.lastLimit());
  }

  /** Forces the next query() to re-download the full result, instead of only its difference with the
   * current one: to be called when the issues held by the server may have changed. */
  invalidateDeltaBaseline() {
    this.lastCriteria.set(undefined);
  }

  /** Loads the per-partition counts, once: they only change when re-syncing sources. */
  loadPartitionStats() {
    if (this.partitionStats()) {
      return;
    }
    this.jiraIssuesService.queryPartitionStats1()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load jira issues partition stats', err)
        },
      });
  }

  /** True when the issue is already held by the cache, and therefore worth refreshing. */
  isCached(key: string): boolean {
    return this.cache.has(key);
  }

  /** Finds an issue by key, from the cached issues if present, otherwise from the server. */
  findByKey(key: string): Observable<JiraIssueDTO> {
    const cached = this.cache.get(key);
    if (cached) {
      return of(cached);
    }
    return this.jiraIssuesService.findIssueByKey(key)
      .pipe(tap((issue) => this.cache.put(issue)));
  }

  /** Finds the keys of the issues nearest to the given one (by issue number, within the same project). */
  findNearbyIssues(key: string): Observable<NearbyJiraIssuesDTO> {
    return this.jiraIssuesService.findNearbyIssues(key);
  }

  /** Re-fetches an issue by key from the server, bypassing the cache, and updates it in the cache. */
  refreshByKey(key: string): Observable<JiraIssueDTO> {
    return this.jiraIssuesService.findIssueByKey(key)
      .pipe(tap((issue) => this.cache.put(issue)));
  }

  /** Downloads the whole result: used for the first search, having nothing to compare with. */
  private queryFull(criteria: IssuesCriteriaDTO, limit: number): Observable<JiraIssueDTO[]> {
    return this.jiraIssuesService.queryIssues({ criteria, limit })
      .pipe(map((issues) => {
        this.putAllInCache(issues);
        return this.publishResult(issues.map((issue) => issue.key!), criteria, limit);
      }));
  }

  /**
   * Re-runs the search that produced the currently held result, unchanged: only the matching keys,
   * each with the last update time of its issue, are downloaded, and only the issues that are not
   * cached yet (newly matching ones), or whose cached copy is older than the server one, are fetched.
   *
   * Nothing but the key+time list therefore travels over the wire when nothing changed server-side,
   * and, unlike the delta query, the criteria is scanned once instead of twice by the server.
   */
  private queryRefreshOutdated(criteria: IssuesCriteriaDTO, limit: number): Observable<JiraIssueDTO[]> {
    return this.jiraIssuesService.queryIssueIdAndLastUpdateTimes({ criteria, limit })
      .pipe(
        switchMap((idAndTimes) => {
          const ids = idAndTimes.map((idAndTime) => idAndTime.id!);
          const outdatedIds = idAndTimes.filter((idAndTime) => this.isOutdated(idAndTime))
              .map((idAndTime) => idAndTime.id!);
          return (outdatedIds.length === 0) ? of(ids)
              : this.fetchIntoCache(outdatedIds).pipe(map(() => ids));
        }),
        map((ids) => this.publishResult(ids, criteria, limit)));
  }

  /** True when the issue is not cached yet, or its cached copy is not the last updated one of the server. */
  private isOutdated(idAndTime: IssueIdAndLastUpdateTimeDTO): boolean {
    const cached = this.cache.get(idAndTime.id!);
    return cached === undefined || parseEpochMillis(cached.fields?.updated) !== (idAndTime.t ?? 0);
  }

  /**
   * Downloads only the difference between the new criteria and the previous one: the server returns
   * the keys matched by only one of them ("left" being the new criteria, "right" the previous one),
   * which is enough to rebuild the new result keys from the previous ones, and only the issues still
   * missing from the cache are then fetched.
   *
   * The right side is queried back with the very limit that produced the currently held keys, so that
   * the server truncates it exactly as it did then, and the comparison stays exact even when the new
   * search uses a different limit.
   */
  private queryByDelta(criteria: IssuesCriteriaDTO, limit: number,
      previousCriteria: IssuesCriteriaDTO, previousLimit: number): Observable<JiraIssueDTO[]> {
    return this.jiraIssuesService.compareQueryIds1({
        leftCriteria: criteria, leftLimit: limit,
        rightCriteria: previousCriteria, rightLimit: previousLimit,
        fillCommonIds: false })
      .pipe(
        switchMap((compared) => {
          const ids = mergeDeltaIds(this.resultIds(), compared);
          const missingIds = this.cache.missingIds(ids);
          return (missingIds.length === 0) ? of(ids)
              : this.fetchIntoCache(missingIds).pipe(map(() => ids));
        }),
        map((ids) => this.publishResult(ids, criteria, limit)));
  }

  /** Fetches the issues having the given keys from the server, into the cache. */
  private fetchIntoCache(ids: string[]): Observable<JiraIssueDTO[]> {
    return this.jiraIssuesService.findIssuesByIds(ids)
      .pipe(tap((issues) => this.putAllInCache(issues)));
  }

  private putAllInCache(issues: JiraIssueDTO[]) {
    try {
      this.cache.putAll(issues);
    }  catch (ex) {
      console.error('Failed putAll', ex, issues);
    }
  }

  /** Publishes the keys of a new result: sorting them here, and not in the query methods, is what
   * makes the full and the delta query return the issues in the very same order.
   *
   * Re-applies the full criteria against the cached issues before publishing: the "refresh outdated"
   * and "delta" query modes only ask the server to compare id sets, so a criteria the server matches
   * more loosely than the client (or a field the client added that the server doesn't know about yet)
   * would otherwise leak stale extra rows into the result. */
  private publishResult(ids: string[], criteria: IssuesCriteriaDTO, limit: number): JiraIssueDTO[] {
    const matchingIds = ids.filter((id) => {
      const issue = this.cache.get(id);
      return issue !== undefined && IssueCriteria.match(criteria, issue);
    });
    this.resultIds.set(matchingIds.sort(compareIssueKeysDesc));
    this.lastCriteria.set(structuredClone(criteria));
    this.lastLimit.set(limit);
    return this.issues();
  }

}
