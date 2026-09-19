/**
 * The "excluded values" enum filters are edited as a Set by their widget, but carried by the criteria
 * DTOs as a plain comma-separated string: these 2 functions convert between both representations.
 */

export function csvToSet(csv: string | undefined): Set<string> {
  return csv ? new Set(csv.split(',')) : new Set();
}

export function setToCsv(values: Set<string>): string {
  return Array.from(values).join(',');
}
