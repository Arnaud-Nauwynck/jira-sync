import { Injectable, computed, signal } from '@angular/core';
import { Observable, map, of, switchMap, tap } from 'rxjs';
import { GitHubPullRequestsService } from '../../rest';
import { GitHubPrCriteriaDTO } from '../../rest';
import { GitHubPrIdAndLastUpdateTimeDTO } from '../../rest';
import { GitHubPrPartitionStatsDTO } from '../../rest';
import { GitHubPullRequestDTO } from '../../rest';
import { NearbyGitHubPullRequestsDTO } from '../../rest';
import { mergeDeltaIds } from '../../utils/criteria-delta';
import { EntityCache } from '../../utils/entity-cache';
import { parseEpochMillis } from '../../utils/epoch-millis';
import { sameCriteria } from '../../utils/same-criteria';
import { GithubPrCriteria } from './GithubPrCriteria';

const DEFAULT_LIMIT = 5000;

/** Orders pull request numbers descending (most recent first). */
function comparePrNumbersDesc(left: number, right: number): number {
  return right - left;
}

@Injectable({ providedIn: 'root' })
export class GithubPullRequestsDataService {

  // Search criteria (and fetch limit) of the github pull requests search page, edited in-place by the criteria
  // view. Held here, in this root-scoped service, so they are not lost when navigating away from and back to the page.
  readonly criteria: GitHubPrCriteriaDTO = {
    fromYear: 2020,
    toYear: 2050,
    draftAvailability: 'any',
    mergedAvailability: 'no',
    mergeableAvailability: 'any',
    analysisAvailability: 'any',
    developmentWorkAvailability: 'any',
    personalInterrestAvailability: 'any',
    excludedStates: 'closed',
  };

  limit = DEFAULT_LIMIT;

  // Every pull request fetched from the server so far, so that changing the criteria only downloads the
  // pull requests newly matched, and not the ones already held (see query()).
  private readonly cache = new EntityCache<number, GitHubPullRequestDTO>((pr) => pr.number!);

  // Numbers of the pull requests matching the last searched criteria, descending.
  private readonly resultIds = signal<number[]>([]);

  // Row Data: the pull requests of the last search, shared with anyone injecting this service.
  readonly pullRequests = computed<GitHubPullRequestDTO[]>(() => {
    this.cache.version(); // re-evaluate whenever the cached PRs change, and not only the result numbers
    return this.cache.getAll(this.resultIds());
  });

  // Snapshot (deep copy) of the criteria, and the limit, that produced the current `pullRequests` result list:
  // a copy, because `criteria` is a mutable object that the criteria view keeps editing in place.
  readonly lastCriteria = signal<GitHubPrCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced PRs per "created_year" partition, loaded once and used to show how many
  // PRs are available versus how many currently match the search criteria.
  readonly partitionStats = signal<GitHubPrPartitionStatsDTO | undefined>(undefined);

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  /** True when the pull request is already held by the cache, and therefore worth refreshing. */
  isCached(number: number): boolean {
    return this.cache.has(number);
  }

  /** Finds a pull request by number, from the cached pull requests if present, otherwise from the server. */
  findByNumber(number: number): Observable<GitHubPullRequestDTO> {
    const cached = this.cache.get(number);
    if (cached) {
      return of(cached);
    }
    return this.gitHubPullRequestsService.findPullRequestByNumber(number)
      .pipe(tap((pullRequest) => this.cache.put(pullRequest)));
  }

  /** Finds the numbers of the PRs nearest to the given one (by PR number). */
  findNearbyPullRequests(number: number): Observable<NearbyGitHubPullRequestsDTO> {
    return this.gitHubPullRequestsService.findNearbyPullRequests(number);
  }

  /** Re-fetches a pull request by number from the server, bypassing the cache, and updates it in the cache. */
  refreshByNumber(number: number): Observable<GitHubPullRequestDTO> {
    return this.gitHubPullRequestsService.findPullRequestByNumber(number)
      .pipe(tap((pullRequest) => this.cache.put(pullRequest)));
  }

  /** True when `pullRequests` already holds the result of exactly this criteria+limit, so re-querying is useless. */
  isUpToDate(criteria: GitHubPrCriteriaDTO, limit: number): boolean {
    const lastCriteria = this.lastCriteria();
    return lastCriteria !== undefined && limit === this.lastLimit() && sameCriteria(lastCriteria, criteria);
  }

  /**
   * Queries the PRs matching the criteria: the returned (cold) Observable performs the query on
   * subscription, and updates the cached `pullRequests` signal with the result.
   *
   * Only what is not held yet is downloaded, which of the 3 query modes applies depending on how the
   * criteria compares with the one that produced the currently held result:
   * - no result held yet: the full result is downloaded (see {@link queryFull});
   * - the very same criteria+limit, re-searched: only the matching numbers and their last update time
   *   are downloaded (see {@link queryRefreshOutdated});
   * - any other criteria: only its difference with the held result is downloaded (see {@link queryByDelta}).
   */
  query(criteria: GitHubPrCriteriaDTO, limit = DEFAULT_LIMIT): Observable<GitHubPullRequestDTO[]> {
    const previousCriteria = this.lastCriteria();
    if (previousCriteria === undefined) {
      return this.queryFull(criteria, limit);
    }
    return this.isUpToDate(criteria, limit)
        ? this.queryRefreshOutdated(criteria, limit)
        : this.queryByDelta(criteria, limit, previousCriteria, this.lastLimit());
  }

  /** Forces the next query() to re-download the full result, instead of only its difference with the
   * current one: to be called when the pull requests held by the server may have changed. */
  invalidateDeltaBaseline() {
    this.lastCriteria.set(undefined);
  }

  /** Loads the per-partition counts, once: they only change when re-syncing sources. */
  loadPartitionStats() {
    if (this.partitionStats()) {
      return;
    }
    this.gitHubPullRequestsService.queryPartitionStats2()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load github pr partition stats', err)
        },
      });
  }

  /** Downloads the whole result: used for the first search, having nothing to compare with. */
  private queryFull(criteria: GitHubPrCriteriaDTO, limit: number): Observable<GitHubPullRequestDTO[]> {
    return this.gitHubPullRequestsService.queryPullRequests({ criteria, limit })
      .pipe(map((pullRequests) => {
        this.cache.putAll(pullRequests);
        return this.publishResult(pullRequests.map((pr) => pr.number!), criteria, limit);
      }));
  }

  /**
   * Re-runs the search that produced the currently held result, unchanged: only the matching numbers,
   * each with the last update time of its pull request, are downloaded, and only the pull requests
   * that are not cached yet (newly matching ones), or whose cached copy is older than the server one,
   * are fetched.
   *
   * Nothing but the number+time list therefore travels over the wire when nothing changed server-side,
   * and, unlike the delta query, the criteria is scanned once instead of twice by the server.
   */
  private queryRefreshOutdated(criteria: GitHubPrCriteriaDTO, limit: number): Observable<GitHubPullRequestDTO[]> {
    return this.gitHubPullRequestsService.queryPullRequestIdAndLastUpdateTimes({ criteria, limit })
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

  /** True when the pull request is not cached yet, or its cached copy is not the last updated one of the server. */
  private isOutdated(idAndTime: GitHubPrIdAndLastUpdateTimeDTO): boolean {
    const cached = this.cache.get(idAndTime.id!);
    return cached === undefined || parseEpochMillis(cached.updatedAt) !== (idAndTime.t ?? 0);
  }

  /**
   * Downloads only the difference between the new criteria and the previous one: the server returns
   * the numbers matched by only one of them ("left" being the new criteria, "right" the previous one),
   * which is enough to rebuild the new result numbers from the previous ones, and only the pull
   * requests still missing from the cache are then fetched.
   *
   * The right side is queried back with the very limit that produced the currently held numbers, so
   * that the server truncates it exactly as it did then, and the comparison stays exact even when the
   * new search uses a different limit.
   */
  private queryByDelta(criteria: GitHubPrCriteriaDTO, limit: number,
      previousCriteria: GitHubPrCriteriaDTO, previousLimit: number): Observable<GitHubPullRequestDTO[]> {
    return this.gitHubPullRequestsService.compareQueryIds2({
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

  /** Fetches the pull requests having the given numbers from the server, into the cache. */
  private fetchIntoCache(ids: number[]): Observable<GitHubPullRequestDTO[]> {
    return this.gitHubPullRequestsService.findByIds1(ids)
      .pipe(tap((pullRequests) => this.cache.putAll(pullRequests)));
  }

  /** Publishes the numbers of a new result: sorting them here, and not in the query methods, is what
   * makes the full and the delta query return the pull requests in the very same order.
   *
   * Re-applies the full criteria against the cached PRs before publishing: the "refresh outdated" and
   * "delta" query modes only ask the server to compare id sets, so a criteria the server matches more
   * loosely than the client (or a field the client added that the server doesn't know about yet) would
   * otherwise leak stale extra rows into the result. */
  private publishResult(ids: number[], criteria: GitHubPrCriteriaDTO, limit: number): GitHubPullRequestDTO[] {
    const matchingIds = ids.filter((id) => {
      const pullRequest = this.cache.get(id);
      return pullRequest !== undefined && GithubPrCriteria.match(criteria, pullRequest);
    });
    this.resultIds.set(matchingIds.sort(comparePrNumbersDesc));
    this.lastCriteria.set(structuredClone(criteria));
    this.lastLimit.set(limit);
    return this.pullRequests();
  }

}
