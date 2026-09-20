/** Escapes every regex metacharacter in `value`, so it can be embedded in a pattern and only ever
 * match itself literally. */
export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
