/** An ISO-8601 date-time offset, written with a ':' separator, without it (Jira style: "+0000"), or as "Z". */
const ISO_OFFSET_SUFFIX = /(?:Z|[+-]\d{2}:?\d{2})$/;

/** The ':'-less offset form, that `Date.parse()` does not accept, split into its hours and minutes. */
const COMPACT_OFFSET_SUFFIX = /([+-]\d{2})(\d{2})$/;

/**
 * Epoch milliseconds of an ISO-8601 date-time text, as the server computes the "last update time"
 * ("t") of an entity from the very same text; 0 when it is absent, or is not a date-time with an
 * offset, exactly as the server returns 0 then.
 */
export function parseEpochMillis(dateTimeText: string | undefined | null): number {
  if (!dateTimeText || !ISO_OFFSET_SUFFIX.test(dateTimeText)) {
    return 0;
  }
  const millis = Date.parse(dateTimeText.replace(COMPACT_OFFSET_SUFFIX, '$1:$2'));
  return isNaN(millis) ? 0 : millis;
}
