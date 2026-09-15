import { Component, Input } from '@angular/core';
import { GitHubPullRequestExtraFieldsDTO } from '../../rest';

@Component({
  selector: 'app-analysis-pr-view',
  templateUrl: './analysis-pr-view.html',
})
export class AnalysisPrView {
  @Input() annotated?: GitHubPullRequestExtraFieldsDTO;
}
