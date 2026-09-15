import { Component, Input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JiraIssueDTO } from '../../rest/model/jiraIssueDTO';
import { JiraIssuesService } from '../../rest';

@Component({
  imports: [FormsModule],
  selector: 'app-personal-interest-issue-view',
  templateUrl: './personal-interest-issue-view.html',
})
export class PersonalInterestIssueView {
  @Input() issue?: JiraIssueDTO;

  editing = signal(false);
  saving = signal(false);
  draftComment = '';
  draftPriority10 = 0;

  constructor(private jiraIssuesService: JiraIssuesService) {}

  toggleEdit() {
    if (this.editing()) {
      this.editing.set(false);
      return;
    }
    this.draftComment = this.issue?.annotated?.personalInterrestComment ?? '';
    this.draftPriority10 = this.issue?.annotated?.personalInterrestPriority10 ?? 0;
    this.editing.set(true);
  }

  save() {
    const key = this.issue?.key;
    if (!key) {
      return;
    }
    this.saving.set(true);
    this.jiraIssuesService.putPersonalInterrestComment1({
      key,
      personalInterrestComment: this.draftComment,
      personalInterrestPriority10: this.draftPriority10,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        if (this.issue) {
          this.issue.annotated = {
            ...this.issue.annotated,
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
