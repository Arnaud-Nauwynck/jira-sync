import { Component, Input } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MailMessageDTO } from '../../rest';
import { MailSourceView } from '../mail-source-view/mail-source-view';
import { AnalysisMailView } from '../analysis-mail-view/analysis-mail-view';
import { WorkDevelopmentMailView } from '../work-development-mail-view/work-development-mail-view';
import { TimelineMailView } from '../timeline-mail-view/timeline-mail-view';
import { PersonalInterestMailView } from '../personal-interest-mail-view/personal-interest-mail-view';

@Component({
  imports: [NgbNavModule, MailSourceView, AnalysisMailView, WorkDevelopmentMailView, TimelineMailView, PersonalInterestMailView],
  selector: 'app-mail-message-view',
  templateUrl: './mail-message-view.html',
})
export class MailMessageView {
  @Input() message?: MailMessageDTO;

  // Active tab: 1 = Mail Source, 2 = Analysis, 3 = Development Work, 4 = Timeline, 5 = Personal Interest.
  active = 1;
}
