import { IssueCriteria } from './IssueCriteria';

/**
 * Orders issue keys by issue number, descending (most recent first). The project prefix is ignored:
 * every issue of the analysed project shares it. Keys without a parsable number sort last.
 *
 * Used to give the search results a stable order, independent of the order in which the server
 * happened to scan its partitions, and therefore identical whether the result was fetched in full
 * or rebuilt from a criteria delta.
 */
export function compareIssueKeysDesc(left: string, right: string): number {
  return (IssueCriteria.issueNumberOf(right) ?? -1) - (IssueCriteria.issueNumberOf(left) ?? -1);
}
