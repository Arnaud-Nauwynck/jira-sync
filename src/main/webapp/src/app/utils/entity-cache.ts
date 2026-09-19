import { Signal, signal } from '@angular/core';

/**
 * In-memory store of the entities fetched from the server, keyed by their id, so that a new search
 * only has to download the entities it does not hold yet (see {@link missingIds}).
 *
 * Unbounded, and only cleared explicitly: an entity dropped by one search and matched again by a
 * later one is then never re-fetched.
 */
export class EntityCache<ID, T> {

  private readonly byId = new Map<ID, T>();

  private readonly writeCount = signal(0);

  /** Incremented on every write, so that `computed()` views derived from this cache re-evaluate. */
  readonly version: Signal<number> = this.writeCount.asReadonly();

  constructor(private readonly idOf: (entity: T) => ID) {}

  has(id: ID): boolean {
    return this.byId.has(id);
  }

  get(id: ID): T | undefined {
    return this.byId.get(id);
  }

  put(entity: T) {
    this.byId.set(this.idOf(entity), entity);
    this.writeCount.update((count) => count + 1);
  }

  putAll(entities: readonly T[]) {
    for (const entity of entities) {
      this.byId.set(this.idOf(entity), entity);
    }
    this.writeCount.update((count) => count + 1);
  }

  /** The ids not held yet, in the given order: what still has to be fetched from the server. */
  missingIds(ids: readonly ID[]): ID[] {
    return ids.filter((id) => !this.byId.has(id));
  }

  /** The entities having the given ids, in the given order; ids not held are silently skipped. */
  getAll(ids: readonly ID[]): T[] {
    const res: T[] = [];
    for (const id of ids) {
      const entity = this.byId.get(id);
      if (entity !== undefined) {
        res.push(entity);
      }
    }
    return res;
  }

  clear() {
    this.byId.clear();
    this.writeCount.update((count) => count + 1);
  }

  get size(): number {
    return this.byId.size;
  }

}
