/**
 * Compares 2 flat criteria DTOs field by field, independently of the order in which their fields
 * were set (a criteria object is progressively filled in, in place, by the criteria views and by the
 * URL query params restore). Fields set to undefined count as absent.
 */
export function sameCriteria(left: object, right: object): boolean {
  const keys = Array.from(new Set([...Object.keys(left), ...Object.keys(right)])).sort();
  return JSON.stringify(left, keys) === JSON.stringify(right, keys);
}
