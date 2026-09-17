
export class Crit {


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
