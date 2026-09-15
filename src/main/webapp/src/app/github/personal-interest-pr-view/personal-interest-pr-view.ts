import { Component, Input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GitHubPullRequestDTO, GitHubPullRequestsService } from '../../rest';

@Component({
  imports: [FormsModule],
  selector: 'app-personal-interest-pr-view',
  templateUrl: './personal-interest-pr-view.html',
})
export class PersonalInterestPrView {
  @Input() pullRequest?: GitHubPullRequestDTO;

  editing = signal(false);
  saving = signal(false);
  draftComment = '';
  draftPriority10 = 0;

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  toggleEdit() {
    if (this.editing()) {
      this.editing.set(false);
      return;
    }
    this.draftComment = this.pullRequest?.annotated?.personalInterrestComment ?? '';
    this.draftPriority10 = this.pullRequest?.annotated?.personalInterrestPriority10 ?? 0;
    this.editing.set(true);
  }

  save() {
    const number = this.pullRequest?.number;
    if (!number) {
      return;
    }
    this.saving.set(true);
    this.gitHubPullRequestsService.putPersonalInterrestComment2({
      number,
      personalInterrestComment: this.draftComment,
      personalInterrestPriority10: this.draftPriority10,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        if (this.pullRequest) {
          this.pullRequest.annotated = {
            ...this.pullRequest.annotated,
            personalInterrestComment: this.draftComment,
            personalInterrestPriority10: this.draftPriority10,
          };
        }
      },
      error: (ex) => {
        this.saving.set(false);
        console.error('... Failed call putPersonalInterrestComment', ex);
      },
    });
  }
}
