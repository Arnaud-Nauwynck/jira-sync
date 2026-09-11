import { Component, Input } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';

@Component({
  imports: [FormsModule],
  selector: 'app-personal-interest-issue-view',
  templateUrl: './personal-interest-issue-view.html',
})
export class PersonalInterestIssueView {
  @Input() issue?: JiraIssueDTO;

  editing = false;
  saving = false;
  draftComment = '';
  draftPriority10 = 0;

  constructor(private http: HttpClient) {}

  toggleEdit() {
    if (this.editing) {
      this.editing = false;
      return;
    }
    this.draftComment = this.issue?.annotated?.personalInterrestComment ?? '';
    this.draftPriority10 = this.issue?.annotated?.personalInterrestPriority10 ?? 0;
    this.editing = true;
  }

  save() {
    const key = this.issue?.key;
    if (!key) {
      return;
    }
    this.saving = true;
    this.http.put(`api/v1/jira-issue-annotations/personnal-interrest`, {
      key,
      personalInterrestComment: this.draftComment,
      personalInterrestPriority10: this.draftPriority10,
    }).subscribe({
      next: () => {
        this.saving = false;
        this.editing = false;
        if (this.issue) {
          this.issue.annotated = {
            ...this.issue.annotated,
            personalInterrestComment: this.draftComment,
            personalInterrestPriority10: this.draftPriority10,
          };
        }
      },
      error: (ex) => {
        this.saving = false;
        console.error('... Failed call http PUT api/v1/jira-issue-annotations/personnal-interrest', ex);
      },
    });
  }
}
