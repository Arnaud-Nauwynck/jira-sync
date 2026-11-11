import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { JiraProjectComponentDTO, JiraProjectVersionDTO, JiraReferentialService } from '../../rest';

@Injectable({ providedIn: 'root' })
export class JiraReferentialDataService {

  private readonly api = inject(JiraReferentialService);

  // Cached on first subscription (lazy), then replayed to every later subscriber without calling the server again.
  readonly projectComponents$: Observable<JiraProjectComponentDTO[]> =
    this.api.getProjectComponents().pipe(shareReplay({ bufferSize: 1, refCount: false }));

  readonly projectVersions$: Observable<JiraProjectVersionDTO[]> =
    this.api.getProjectVersions().pipe(shareReplay({ bufferSize: 1, refCount: false }));

}
