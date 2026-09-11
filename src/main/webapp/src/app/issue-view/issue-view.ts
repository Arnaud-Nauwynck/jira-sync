import { Component, Input } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';
import { JiraIssueView } from '../jira-issue-view/jira-issue-view';
import { AnalysisIssueView } from '../analysis-issue-view/analysis-issue-view';
import { WorkDevelopmentIssueView } from '../work-development-issue-view/work-development-issue-view';
import { TimelineIssueView } from '../timeline-issue-view/timeline-issue-view';
import { PersonalInterestIssueView } from '../personal-interest-issue-view/personal-interest-issue-view';

@Component({
  imports: [NgbNavModule, JiraIssueView, AnalysisIssueView, WorkDevelopmentIssueView, TimelineIssueView, PersonalInterestIssueView],
  selector: 'app-issue-view',
  templateUrl: './issue-view.html',
})
export class IssueView {
  @Input() issue?: JiraIssueDTO;

  // Active tab: 1 = Jira Source, 2 = Analysis, 3 = Development Work, 4 = Timeline, 5 = Personal Interest.
  active = 1;
}
