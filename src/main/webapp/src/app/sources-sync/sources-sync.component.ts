import { Component, OnInit, signal } from '@angular/core';
import { GitHubSyncService } from '../rest/api/gitHubSync.service';
import { JiraSyncService } from '../rest/api/jiraSync.service';
import { MailingListSyncService } from '../rest/api/mailingListSync.service';

@Component({
  imports: [],
  selector: 'app-sources-sync',
  templateUrl: './sources-sync.component.html',
})
export class SourcesSync implements OnInit {
  showAdvanced = signal(false);

  running = signal(false);
  githubRunning = signal(false);
  githubCompletingReviewComments = signal(false);
  githubCompletingComments = signal(false);
  mailingListRunning = signal(false);

  lastSyncTime = signal<string | undefined>(undefined);
  githubLastSyncTime = signal<string | undefined>(undefined);
  mailingListLastClosedMonth = signal<string | undefined>(undefined);

  constructor(
    private jiraSyncService: JiraSyncService,
    private gitHubSyncService: GitHubSyncService,
    private mailingListSyncService: MailingListSyncService,
  ) {}

  ngOnInit() {
    this.refreshLastSync();
    this.refreshGithubLastSync();
    this.refreshMailingListLastSync();
  }

  refreshLastSync() {
    this.jiraSyncService.getLastSync1().subscribe({
      next: (status) => this.lastSyncTime.set(status.lastSyncTime),
      error: (ex) => console.error("... Failed to load last sync info for jira-sync", ex),
    });
  }

  refreshGithubLastSync() {
    this.gitHubSyncService.getLastSync2().subscribe({
      next: (status) => this.githubLastSyncTime.set(status.lastSyncTime),
      error: (ex) => console.error("... Failed to load last sync info for github-sync", ex),
    });
  }

  refreshMailingListLastSync() {
    this.mailingListSyncService.getLastSync().subscribe({
      next: (status) => this.mailingListLastClosedMonth.set(status.lastClosedMonth),
      error: (ex) => console.error("... Failed to load last sync info for mailing-list-sync", ex),
    });
  }

  runSyncAll() {
    this.running.set(true);
    console.log("call http POST api/v1/jira-sync/run-sync-all");
    this.jiraSyncService.runSyncAll1().subscribe({
      next: () => {
        this.running.set(false);
        console.log("... done call http POST api/v1/jira-sync/run-sync-all");
        this.refreshLastSync();
      },
      error: (ex) => {
        this.running.set(false);
        console.error("... Failed call http POST api/v1/jira-sync/run-sync-all", ex);
      }
    });
  }

  runGitHubSyncAll() {
    this.githubRunning.set(true);
    console.log("call http POST api/v1/github-sync/run-sync-all");
    this.gitHubSyncService.runSyncAll2().subscribe({
      next: () => {
        this.githubRunning.set(false);
        console.log("... done call http POST api/v1/github-sync/run-sync-all");
        this.refreshGithubLastSync();
      },
      error: (ex) => {
        this.githubRunning.set(false);
        console.error("... Failed call http POST api/v1/github-sync/run-sync-all", ex);
      }
    });
  }

  completeMissingReviewComments() {
    this.githubCompletingReviewComments.set(true);
    console.log("call http POST api/v1/github-sync/complete-missing-review-comments");
    this.gitHubSyncService.completeMissingReviewComments().subscribe({
      next: () => {
        this.githubCompletingReviewComments.set(false);
        console.log("... done call http POST api/v1/github-sync/complete-missing-review-comments");
      },
      error: (ex) => {
        this.githubCompletingReviewComments.set(false);
        console.error("... Failed call http POST api/v1/github-sync/complete-missing-review-comments", ex);
      }
    });
  }

  completeMissingComments() {
    this.githubCompletingComments.set(true);
    console.log("call http POST api/v1/github-sync/complete-missing-comments");
    this.gitHubSyncService.completeMissingComments().subscribe({
      next: () => {
        this.githubCompletingComments.set(false);
        console.log("... done call http POST api/v1/github-sync/complete-missing-comments");
      },
      error: (ex) => {
        this.githubCompletingComments.set(false);
        console.error("... Failed call http POST api/v1/github-sync/complete-missing-comments", ex);
      }
    });
  }

  runMailingListSyncAll() {
    this.mailingListRunning.set(true);
    console.log("call http POST api/v1/mailing-list-sync/run-sync-all");
    this.mailingListSyncService.runSyncAll().subscribe({
      next: () => {
        this.mailingListRunning.set(false);
        console.log("... done call http POST api/v1/mailing-list-sync/run-sync-all");
        this.refreshMailingListLastSync();
      },
      error: (ex) => {
        this.mailingListRunning.set(false);
        console.error("... Failed call http POST api/v1/mailing-list-sync/run-sync-all", ex);
      }
    });
  }
}
