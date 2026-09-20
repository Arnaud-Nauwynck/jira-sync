/** Clears every field of a criteria DTO in place (sets it to `undefined`), so a fresh unfiltered
 * search can be issued. The object reference is kept, since it is owned by the data service and
 * bound in-place by the criteria views (see {@link sameCriteria}, which treats `undefined` as absent). */
export function clearCriteriaFields(criteria: object): void {
  Object.keys(criteria).forEach((key) => {
    (criteria as Record<string, unknown>)[key] = undefined;
  });
}
