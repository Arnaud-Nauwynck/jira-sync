import { Component, Input } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPullRequestDTO } from '../rest/model/gitHubPullRequestDTO';

@Component({
  imports: [NgbNavModule],
  selector: 'app-github-pr-view',
  templateUrl: './github-pr-view.html',
})
export class GithubPrView {
  @Input() pullRequest?: GitHubPullRequestDTO;

  // Active tab: 1 = Conversation, 2 = Commits, 3 = Checks, 4 = Files changed.
  active = 1;

  /** The GitHub commit page URL for the given sha, derived from the PR's htmlUrl (".../pull/{number}" -> ".../commit/{sha}"). */
  commitUrl(pr: GitHubPullRequestDTO, sha: string): string | undefined {
    return pr.htmlUrl?.replace(/\/pull\/\d+$/, `/commit/${sha}`);
  }
}
