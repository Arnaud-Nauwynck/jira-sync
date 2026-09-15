import { Component, Input } from '@angular/core';
import { GitHubPullRequestExtraFieldsDTO } from '../../rest';

@Component({
  selector: 'app-work-development-pr-view',
  templateUrl: './work-development-pr-view.html',
})
export class WorkDevelopmentPrView {
  @Input() annotated?: GitHubPullRequestExtraFieldsDTO;
}
