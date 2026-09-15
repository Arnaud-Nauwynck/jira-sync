import { Component, Input } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPullRequestDTO, GitHubPullRequestReviewCommentDTO } from '../../rest';

@Component({
  imports: [NgbNavModule],
  selector: 'app-github-source-pr-view',
  templateUrl: './github-source-pr-view.html',
})
export class GitHubSourcePrView {
  @Input() pullRequest?: GitHubPullRequestDTO;

  // Active tab: 1 = Conversation, 2 = Commits, 3 = Checks, 4 = Files changed.
  active = 1;

  /** The GitHub commit page URL for the given sha, derived from the PR's htmlUrl (".../pull/{number}" -> ".../commit/{sha}"). */
  commitUrl(pr: GitHubPullRequestDTO, sha: string): string | undefined {
    return pr.htmlUrl?.replace(/\/pull\/\d+$/, `/commit/${sha}`);
  }

  /** Review comments sorted chronologically, oldest first, for display in the Conversation tab. */
  sortedReviewComments(pr: GitHubPullRequestDTO): GitHubPullRequestReviewCommentDTO[] {
    const comments = pr.reviewCommentsData ?? [];
    return [...comments].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''));
  }
}
