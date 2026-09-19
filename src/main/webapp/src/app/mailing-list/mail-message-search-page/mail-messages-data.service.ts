import { Injectable, computed, signal } from '@angular/core';
import { Observable, map, of, switchMap, tap } from 'rxjs';
import { MailMessageCriteriaDTO, MailMessageIdAndLastUpdateTimeDTO, MailMessagePartitionStatsDTO, MailMessageDTO, MailMessagesService, NearbyMailMessagesDTO } from '../../rest';
import { mergeDeltaIds } from '../../utils/criteria-delta';
import { EntityCache } from '../../utils/entity-cache';
import { parseEpochMillis } from '../../utils/epoch-millis';
import { extractMessageId } from '../../utils/mail-message-id';
import { sameCriteria } from '../../utils/same-criteria';

const DEFAULT_LIMIT = 5000;

// Date of a message, as a comparable number; messages without a parsable Date sort last in a
// descending order. Unlike a jira issue key or a github PR number, a Message-ID carries no order at
// all, so the result is ordered on the Date of the messages, and not on their ids.
function timeOf(message: MailMessageDTO | undefined): number {
  const time = message?.date ? Date.parse(message.date) : NaN;
  return Number.isNaN(time) ? -Infinity : time;
}

@Injectable({ providedIn: 'root' })
export class MailMessagesDataService {

  // Search criteria (and fetch limit) of the mailing-list search page, edited in-place by the criteria view.
  // Held here, in this root-scoped service, so they are not lost when navigating away from and back to the page.
  readonly criteria: MailMessageCriteriaDTO = {
    analysisAvailability: 'any',
    developmentWorkAvailability: 'any',
    personalInterrestAvailability: 'any',
  };

  limit = DEFAULT_LIMIT;

  // Every message fetched from the server so far, so that changing the criteria only downloads the
  // messages newly matched, and not the ones already held (see query()).
  private readonly cache = new EntityCache<string, MailMessageDTO>((message) => message.messageId!);

  // Message-IDs of the messages matching the last searched criteria, ordered by Date descending.
  private readonly resultIds = signal<string[]>([]);

  // Row Data: the messages of the last search, shared with anyone injecting this service.
  readonly messages = computed<MailMessageDTO[]>(() => {
    this.cache.version(); // re-evaluate whenever the cached messages change, and not only the result ids
    return this.cache.getAll(this.resultIds());
  });

  // Snapshot (deep copy) of the criteria, and the limit, that produced the current `messages` result list:
  // a copy, because `criteria` is a mutable object that the criteria view keeps editing in place.
  readonly lastCriteria = signal<MailMessageCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced messages per "archived" (month) partition and per sender, loaded once and
  // used to show how many messages are available versus how many currently match the search criteria.
  readonly partitionStats = signal<MailMessagePartitionStatsDTO | undefined>(undefined);

  constructor(private mailMessagesService: MailMessagesService) {}

  /** True when the message is already held by the cache, and therefore worth refreshing. */
  isCached(messageId: string): boolean {
    return this.cache.has(extractMessageId(messageId));
  }

  /**
   * Finds a message by messageId (optionally "{yyyy-MM-dd}-" prefixed, see {@link extractMessageId}),
   * from the cached messages if present, otherwise from the server. The date-prefixed form is passed
   * through as-is to the server so it can load only the relevant partition.
   */
  findByMessageId(dateMessageIdOrMessageId: string): Observable<MailMessageDTO> {
    const messageId = extractMessageId(dateMessageIdOrMessageId);
    const cached = this.cache.get(messageId);
    if (cached) {
      return of(cached);
    }
    return this.mailMessagesService.findMessageByMessageId(dateMessageIdOrMessageId)
      .pipe(tap((message) => this.cache.put(message)));
  }

  /** Finds the Message-IDs of the messages nearest to the given one (by Date, overall and from the same sender). */
  findNearbyMessages(messageId: string): Observable<NearbyMailMessagesDTO> {
    return this.mailMessagesService.findNearbyMessages(extractMessageId(messageId));
  }

  /** Re-fetches a message by messageId from the server, bypassing the cache, and updates it in the cache. */
  refreshByMessageId(messageId: string): Observable<MailMessageDTO> {
    return this.mailMessagesService.findMessageByMessageId(messageId)
      .pipe(tap((message) => this.cache.put(message)));
  }

  /** True when `messages` already holds the result of exactly this criteria+limit, so re-querying is useless. */
  isUpToDate(criteria: MailMessageCriteriaDTO, limit: number): boolean {
    const lastCriteria = this.lastCriteria();
    return lastCriteria !== undefined && limit === this.lastLimit() && sameCriteria(lastCriteria, criteria);
  }

  /**
   * Queries the messages matching the criteria: the returned (cold) Observable performs the query on
   * subscription, and updates the cached `messages` signal with the result.
   *
   * Only what is not held yet is downloaded, which of the 3 query modes applies depending on how the
   * criteria compares with the one that produced the currently held result:
   * - no result held yet: the full result is downloaded (see {@link queryFull});
   * - the very same criteria+limit, re-searched: only the matching ids and their last update time are
   *   downloaded (see {@link queryRefreshOutdated});
   * - any other criteria: only its difference with the held result is downloaded (see {@link queryByDelta}).
   */
  query(criteria: MailMessageCriteriaDTO, limit = DEFAULT_LIMIT): Observable<MailMessageDTO[]> {
    const previousCriteria = this.lastCriteria();
    if (previousCriteria === undefined) {
      return this.queryFull(criteria, limit);
    }
    return this.isUpToDate(criteria, limit)
        ? this.queryRefreshOutdated(criteria, limit)
        : this.queryByDelta(criteria, limit, previousCriteria, this.lastLimit());
  }

  /** Forces the next query() to re-download the full result, instead of only its difference with the
   * current one: to be called when the messages held by the server may have changed. */
  invalidateDeltaBaseline() {
    this.lastCriteria.set(undefined);
  }

  /** Loads the per-partition counts, once: they only change when re-syncing sources. */
  loadPartitionStats() {
    if (this.partitionStats()) {
      return;
    }
    this.mailMessagesService.queryPartitionStats()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load mailing-list partition stats', err)
        },
      });
  }

  /** Downloads the whole result: used for the first search, having nothing to compare with. */
  private queryFull(criteria: MailMessageCriteriaDTO, limit: number): Observable<MailMessageDTO[]> {
    return this.mailMessagesService.queryMessages({ criteria, limit })
      .pipe(map((messages) => {
        this.cache.putAll(messages);
        return this.publishResult(messages.map((message) => message.messageId!), criteria, limit);
      }));
  }

  /**
   * Re-runs the search that produced the currently held result, unchanged: only the matching ids, each
   * with the last update time of its message, are downloaded, and only the messages that are not
   * cached yet (newly matching ones), or whose cached copy is older than the server one, are fetched.
   *
   * Nothing but the id+time list therefore travels over the wire when nothing changed server-side,
   * and, unlike the delta query, the criteria is scanned once instead of twice by the server.
   *
   * The server returns each id in its "{yyyy-MM-dd}-{messageId}" display form (see
   * {@code MailMessageDTO#id()}); it is normalized back to the plain Message-ID right away, since
   * that is what the cache and `resultIds` are keyed by.
   */
  private queryRefreshOutdated(criteria: MailMessageCriteriaDTO, limit: number): Observable<MailMessageDTO[]> {
    return this.mailMessagesService.queryMessageIdAndLastUpdateTimes({ criteria, limit })
      .pipe(
        switchMap((idAndTimes) => {
          const ids = idAndTimes.map((idAndTime) => extractMessageId(idAndTime.id!));
          const outdatedIds = idAndTimes.filter((idAndTime) => this.isOutdated(idAndTime))
              .map((idAndTime) => extractMessageId(idAndTime.id!));
          return (outdatedIds.length === 0) ? of(ids)
              : this.fetchIntoCache(outdatedIds).pipe(map(() => ids));
        }),
        map((ids) => this.publishResult(ids, criteria, limit)));
  }

  /** True when the message is not cached yet, or its cached copy is not the last updated one of the server. */
  private isOutdated(idAndTime: MailMessageIdAndLastUpdateTimeDTO): boolean {
    const cached = this.cache.get(extractMessageId(idAndTime.id!));
    return cached === undefined || parseEpochMillis(cached.date) !== (idAndTime.t ?? 0);
  }

  /**
   * Downloads only the difference between the new criteria and the previous one: the server returns
   * the ids matched by only one of them ("left" being the new criteria, "right" the previous one),
   * which is enough to rebuild the new result ids from the previous ones, and only the messages still
   * missing from the cache are then fetched.
   *
   * The right side is queried back with the very limit that produced the currently held ids, so that
   * the server truncates it exactly as it did then, and the comparison stays exact even when the new
   * search uses a different limit.
   *
   * The server returns each id in its "{yyyy-MM-dd}-{messageId}" display form (see
   * {@code MailMessageDTO#id()}); both sides are normalized back to the plain Message-ID before
   * merging, since that is what `resultIds` and the cache are keyed by.
   */
  private queryByDelta(criteria: MailMessageCriteriaDTO, limit: number,
      previousCriteria: MailMessageCriteriaDTO, previousLimit: number): Observable<MailMessageDTO[]> {
    return this.mailMessagesService.compareQueryIds({
        leftCriteria: criteria, leftLimit: limit,
        rightCriteria: previousCriteria, rightLimit: previousLimit,
        fillCommonIds: false })
      .pipe(
        switchMap((compared) => {
          const normalized = {
            leftOnlyIds: (compared.leftOnlyIds ?? []).map(extractMessageId),
            rightOnlyIds: (compared.rightOnlyIds ?? []).map(extractMessageId),
          };
          const ids = mergeDeltaIds(this.resultIds(), normalized);
          const missingIds = this.cache.missingIds(ids);
          return (missingIds.length === 0) ? of(ids)
              : this.fetchIntoCache(missingIds).pipe(map(() => ids));
        }),
        map((ids) => this.publishResult(ids, criteria, limit)));
  }

  /** Fetches the messages having the given Message-IDs from the server, into the cache. */
  private fetchIntoCache(ids: string[]): Observable<MailMessageDTO[]> {
    return this.mailMessagesService.findByIds(ids)
      .pipe(tap((messages) => this.cache.putAll(messages)));
  }

  /** Publishes the ids of a new result: sorting them here, and not in the query methods, is what
   * makes the full and the delta query return the messages in the very same order. */
  private publishResult(ids: string[], criteria: MailMessageCriteriaDTO, limit: number): MailMessageDTO[] {
    this.resultIds.set([...ids]
        .sort((left, right) => timeOf(this.cache.get(right)) - timeOf(this.cache.get(left))));
    this.lastCriteria.set(structuredClone(criteria));
    this.lastLimit.set(limit);
    return this.messages();
  }

}
