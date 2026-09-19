import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

/** Routes of the (heavy, ag-grid based) search pages, kept alive when navigating away from them. */
const RETAINED_PATHS = new Set(['issues', 'github-pull-requests', 'mailing-list']);

/**
 * Keeps the search-page components alive (detached, not destroyed) when navigating away, and
 * re-attaches them on return: the loaded rows, the ag-grid column/sort/scroll state, the open
 * master-detail panel and the criteria widgets are all restored as the user left them, instead of
 * being rebuilt from scratch and re-queried.
 *
 * Every other route keeps the default behaviour (destroyed on leave, recreated on entry).
 */
export class SearchPageRouteReuseStrategy implements RouteReuseStrategy {

  private readonly detachedHandles = new Map<string, DetachedRouteHandle>();

  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return this.retainedPathOf(route) !== undefined;
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    const path = this.retainedPathOf(route);
    if (!path) {
      return;
    }
    if (handle) {
      this.detachedHandles.set(path, handle);
    } else {
      this.detachedHandles.delete(path);
    }
  }

  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    const path = this.retainedPathOf(route);
    return !!path && this.detachedHandles.has(path);
  }

  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    const path = this.retainedPathOf(route);
    return (path && this.detachedHandles.get(path)) || null;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig;
  }

  private retainedPathOf(route: ActivatedRouteSnapshot): string | undefined {
    const path = route.routeConfig?.path;
    return path && RETAINED_PATHS.has(path) ? path : undefined;
  }
}
