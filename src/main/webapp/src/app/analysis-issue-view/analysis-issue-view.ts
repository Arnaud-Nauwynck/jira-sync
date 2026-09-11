import { Component, Input } from '@angular/core';
import { IssueExtraFieldsDTO } from '../rest/model/issueExtraFieldsDTO';

@Component({
  selector: 'app-analysis-issue-view',
  templateUrl: './analysis-issue-view.html',
})
export class AnalysisIssueView {
  @Input() annotated?: IssueExtraFieldsDTO;
}
