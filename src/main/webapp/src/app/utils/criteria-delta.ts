/**
 * Structural subset of the generated `*CompareIdsResultDTO` of each module (jira / github /
 * mailing-list), holding only what {@link mergeDeltaIds} needs.
 */
export interface CompareIdsResultLike<ID> {
  leftOnlyIds?: Array<ID>;
  rightOnlyIds?: Array<ID>;
}

/**
 * Ids matched by the "left" (new) criteria, derived from the ids already known to be matched by the
 * "right" (previous) criteria: the previously matched ids that the new criteria drops, minus, and
 * the ids that it adds, plus.
 *
 * The ids matched by both criteria therefore never have to travel over the wire: only the symmetric
 * difference of the 2 result sets does, which is tiny when the criteria was only slightly edited.
 *
 * The resulting order is meaningless (it is neither side's query order); the caller sorts.
 */
export function mergeDeltaIds<ID>(previousIds: readonly ID[], compared: CompareIdsResultLike<ID>): ID[] {
  const droppedIds = new Set(compared.rightOnlyIds ?? []);
  return [...previousIds.filter((id) => !droppedIds.has(id)), ...(compared.leftOnlyIds ?? [])];
}
