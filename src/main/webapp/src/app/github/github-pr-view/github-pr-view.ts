import { Component, Input } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPullRequestDTO } from '../../rest';
import { GitHubSourcePrView } from '../github-source-pr-view/github-source-pr-view';
import { AnalysisPrView } from '../analysis-pr-view/analysis-pr-view';
import { WorkDevelopmentPrView } from '../work-development-pr-view/work-development-pr-view';
import { TimelinePrView } from '../timeline-pr-view/timeline-pr-view';
import { PersonalInterestPrView } from '../personal-interest-pr-view/personal-interest-pr-view';

@Component({
  imports: [NgbNavModule, GitHubSourcePrView, AnalysisPrView, WorkDevelopmentPrView, TimelinePrView, PersonalInterestPrView],
  selector: 'app-github-pr-view',
  templateUrl: './github-pr-view.html',
})
export class GithubPrView {
  @Input() pullRequest?: GitHubPullRequestDTO;

  // Active tab: 1 = Github Source, 2 = Analysis, 3 = Development Work, 4 = Timeline, 5 = Personal Interest.
  active = 1;
}
