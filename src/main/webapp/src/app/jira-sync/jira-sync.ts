import { Component, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  imports: [],
  selector: 'app-jira-sync',
  templateUrl: './jira-sync.html',
})
export class JiraSync {
  running = signal(false);

  constructor(private http: HttpClient) {}

  runSyncAll() {
    this.running.set(true);
    console.log("call http POST api/v1/jira-sync/run-sync-all");
    this.http.post('api/v1/jira-sync/run-sync-all', {}).subscribe({
      next: () => {
        this.running.set(false);
        console.log("... done call http POST api/v1/jira-sync/run-sync-all");
      },
      error: (ex) => {
        this.running.set(false);
        console.error("... Failed call http POST api/v1/jira-sync/run-sync-all", ex);
      }
    });
  }
}
