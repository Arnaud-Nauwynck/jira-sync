import { Component, OnInit, signal } from '@angular/core';
import { GitHubSyncService } from '../../rest/api/gitHubSync.service';
import { RateLimit } from '../../rest/model/rateLimit';

interface RateLimitRow {
  name: string;
  limit?: number;
  remaining?: number;
  used?: number;
  resetAt?: string;
}

@Component({
  imports: [],
  selector: 'app-github-rate-limit',
  templateUrl: './github-rate-limit.html',
})
export class GithubRateLimit implements OnInit {
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  rows = signal<RateLimitRow[]>([]);

  constructor(private gitHubSyncService: GitHubSyncService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set(undefined);
    this.gitHubSyncService.getRateLimit().subscribe({
      next: (dto) => {
        this.loading.set(false);
        const resources = dto.resources ?? {};
        const rows: RateLimitRow[] = Object.entries(resources)
          .filter(([, value]) => value != null)
          .map(([name, value]) => this.toRow(name, value as RateLimit));
        this.rows.set(rows);
      },
      error: (ex) => {
        this.loading.set(false);
        this.error.set('Failed to load GitHub rate limit: ' + (ex?.message ?? ex));
        console.error('... Failed call http GET api/v1/github-sync/rate-limit', ex);
      },
    });
  }

  private toRow(name: string, rateLimit: RateLimit): RateLimitRow {
    return {
      name,
      limit: rateLimit.limit,
      remaining: rateLimit.remaining,
      used: rateLimit.used,
      resetAt: rateLimit.reset != null ? new Date(rateLimit.reset * 1000).toLocaleString() : undefined,
    };
  }
}
