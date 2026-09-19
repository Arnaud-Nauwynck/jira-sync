import { Params } from '@angular/router';

/**
 * Serialization of a flat search-criteria DTO (+ its fetch `limit`) to/from the URL query params,
 * so a search is bookmarkable, shareable, and restored on a browser reload / Back-Forward.
 *
 * Only the criteria fields that are actually set are written; on the way back, the params are
 * applied in place onto the existing criteria object (which the criteria view keeps editing).
 */

/** Query param holding the server-fetch cap, alongside the criteria fields. */
const LIMIT_PARAM = 'limit';

export function criteriaToQueryParams(criteria: object, limit: number): Params {
  const params: Params = {};
  for (const [key, value] of Object.entries(criteria)) {
    if (value !== undefined && value !== null && value !== '') {
      params[key] = String(value);
    }
  }
  params[LIMIT_PARAM] = String(limit);
  return params;
}

/**
 * Applies the criteria fields found in `params` onto `criteria`, in place, converting back to
 * `number` the fields listed in `numericKeys` (query params are always strings).
 * Returns the `limit` param when present and numeric, otherwise undefined.
 */
export function applyQueryParamsToCriteria(criteria: object, params: Params, numericKeys: readonly string[]): number | undefined {
  const numerics = new Set(numericKeys);
  const target = criteria as Record<string, string | number>;
  for (const [key, rawValue] of Object.entries(params)) {
    if (key === LIMIT_PARAM) {
      continue;
    }
    const value = Array.isArray(rawValue) ? rawValue[0] : rawValue;
    target[key] = numerics.has(key) ? Number(value) : value;
  }
  const limit = Number(params[LIMIT_PARAM]);
  return Number.isFinite(limit) && limit > 0 ? limit : undefined;
}
