import { Component, Input } from '@angular/core';
import { IssueExtraFieldsDTO } from '../rest/model/issueExtraFieldsDTO';

@Component({
  selector: 'app-work-development-issue-view',
  templateUrl: './work-development-issue-view.html',
})
export class WorkDevelopmentIssueView {
  @Input() annotated?: IssueExtraFieldsDTO;
}
