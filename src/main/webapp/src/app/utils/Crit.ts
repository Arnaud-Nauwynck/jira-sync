
/** Result of comparing how restrictive two criteria (or a single field of them) are against each
 * other: -1 = the first matches a superset of what the second matches (less restrictive), +1 = the
 * first matches a subset (more restrictive), 0 = equally restrictive, null = neither is a superset
 * of the other (not comparable). */
export type Restrictiveness = -1 | 0 | 1 | null;

export class Crit {

  /** Combines per-field {@link Restrictiveness} comparisons into one overall result: null as soon as
   * any field is not comparable, or once fields disagree on direction (some less, some more
   * restrictive); otherwise the common non-zero direction, or 0 if every field is equal. */
  public static combineRestrictiveness(...parts: Restrictiveness[]): Restrictiveness {
    let result: Restrictiveness = 0;
    for (const part of parts) {
      if (part == null) {
        return null;
      }
      if (part !== 0) {
        if (result !== 0 && result !== part) {
          return null;
        }
        result = part;
      }
    }
    return result;
  }

  /** Restrictiveness of the [minA,maxA] range versus [minB,maxB] (either bound unset meaning
   * unbounded on that side): -1 if A's range contains B's (A less restrictive), +1 if B's contains
   * A's, 0 if the same, null if neither range contains the other. */
  public static compareRange<T extends number | string>(
    minA: T | undefined, maxA: T | undefined, minB: T | undefined, maxB: T | undefined,
  ): Restrictiveness {
    const aWiderLow = minA == null || (minB != null && minA <= minB);
    const aWiderHigh = maxA == null || (maxB != null && maxA >= maxB);
    const bWiderLow = minB == null || (minA != null && minB <= minA);
    const bWiderHigh = maxB == null || (maxA != null && maxB >= maxA);
    const aContainsB = aWiderLow && aWiderHigh;
    const bContainsA = bWiderLow && bWiderHigh;
    if (aContainsB && bContainsA) {
      return 0;
    }
    if (aContainsB) {
      return -1;
    }
    if (bContainsA) {
      return 1;
    }
    return null;
  }

  /** Restrictiveness of an OR-combined "contains any of these comma-separated terms" filter (as
   * matched by {@link matchesAny}): unset/empty is unrestricted (matches everything), and among two
   * non-empty term sets, the one whose terms are a subset of the other's matches fewer items and is
   * therefore the more restrictive one. */
  public static compareContainsFilter(a: string | undefined, b: string | undefined): Restrictiveness {
    const termsA = new Set(Crit.parseCsvList(a).map((t) => t.toLowerCase()));
    const termsB = new Set(Crit.parseCsvList(b).map((t) => t.toLowerCase()));
    if (termsA.size === 0 && termsB.size === 0) {
      return 0;
    }
    if (termsA.size === 0) {
      return -1;
    }
    if (termsB.size === 0) {
      return 1;
    }
    const aSubsetB = [...termsA].every((t) => termsB.has(t));
    const bSubsetA = [...termsB].every((t) => termsA.has(t));
    if (aSubsetB && bSubsetA) {
      return 0;
    }
    if (aSubsetB) {
      return 1;
    }
    if (bSubsetA) {
      return -1;
    }
    return null;
  }

  /** Restrictiveness of a comma-separated "excluded values" filter (as matched by {@link isExcluded}):
   * excluding a subset of what the other excludes lets through more, so it is the less restrictive one. */
  public static compareExcludedFilter(a: string | undefined, b: string | undefined): Restrictiveness {
    const setA = new Set(Crit.parseCsvList(a));
    const setB = new Set(Crit.parseCsvList(b));
    if (setA.size === 0 && setB.size === 0) {
      return 0;
    }
    const aSubsetB = [...setA].every((v) => setB.has(v));
    const bSubsetA = [...setB].every((v) => setA.has(v));
    if (aSubsetB && bSubsetA) {
      return 0;
    }
    if (aSubsetB) {
      return -1;
    }
    if (bSubsetA) {
      return 1;
    }
    return null;
  }

  /** Restrictiveness of a tri-state 'yes'/'no'/'any' filter: 'any' is unrestricted; 'yes' versus 'no'
   * match disjoint sets, so neither is a superset of the other. */
  public static compareYesNoAny(a: string | undefined, b: string | undefined): Restrictiveness {
    const av = a ?? 'any';
    const bv = b ?? 'any';
    if (av === bv) {
      return 0;
    }
    if (av === 'any') {
      return -1;
    }
    if (bv === 'any') {
      return 1;
    }
    return null;
  }

  /** Restrictiveness of a full-match regex pattern filter: a blank pattern is unrestricted; two
   * different non-blank patterns are not safely comparable in general. */
  public static compareRegex(a: string | undefined, b: string | undefined): Restrictiveness {
    const av = (a ?? '').trim();
    const bv = (b ?? '').trim();
    if (av === bv) {
      return 0;
    }
    if (av === '') {
      return -1;
    }
    if (bv === '') {
      return 1;
    }
    return null;
  }

  /** Full-match regex test; a blank pattern always matches, and an invalid regex is treated as no filter. */
  public static matchesRegex(patternText: string | undefined, value: string | undefined): boolean {
    const text = (patternText ?? '').trim();
    if (!text) {
      return true;
    }
    if (value == null) {
      return false;
    }
    try {
      return new RegExp(`^(?:${text})$`).test(value);
    } catch {
      return true;
    }
  }

  public static matchesAny(filterValue: string | undefined, ...values: (string | undefined)[]): boolean {
    const terms = Crit.parseCsvList(filterValue);
    if (terms.length === 0) {
      return true;
    }
    return values.some((value) =>
      value != null && terms.some((term) => value.toLowerCase().includes(term.toLowerCase())));
  }

  /** Whether the value is in the comma-separated excluded list. */
  public static isExcluded(excludedCsv: string | undefined, value: string): boolean {
    return Crit.parseCsvList(excludedCsv).includes(value);
  }

  public static matchesDateRange(from: string | undefined, to: string | undefined, value: string | undefined): boolean {
    if (!from && !to) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (from && value < from) {
      return false;
    }
    if (to && value > `${to}T23:59:59`) {
      return false;
    }
    return true;
  }

  public static matchesNumberRange(min: number | undefined, max: number | undefined, value: number | undefined): boolean {
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
  public static matchesTokensRangeK(minK: number | undefined, maxK: number | undefined, value: number | undefined): boolean {
    if (minK == null && maxK == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (minK != null && value < minK * 1000) {
      return false;
    }
    if (maxK != null && value > maxK * 1000) {
      return false;
    }
    return true;
  }


  public static matchesYesNoAny(filter: string | undefined, value: boolean): boolean {
    if (filter === 'yes') {
      return value;
    }
    if (filter === 'no') {
      return !value;
    }
    return true;
  }

  /** Whether the created date's year falls within [fromYear, toYear] (inclusive); unparsable/missing dates pass through. */
  public static matchesDateTextInYearRange(fromYear: number|undefined, toYear: number|undefined, created: string | undefined): boolean {
    if (created == null) {
      return true;
    }
    const year = parseInt(created.substring(0, 4), 10);
    if (isNaN(year)) {
      return true;
    }
    return (fromYear == null || year >= fromYear) && (toYear == null || year <= toYear);
  }


  public static parseCsvList(value: string | undefined): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

}
