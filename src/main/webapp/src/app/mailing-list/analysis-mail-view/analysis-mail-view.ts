import { Component, Input } from '@angular/core';
import { MailMessageExtraFieldsDTO } from '../../rest';

@Component({
  selector: 'app-analysis-mail-view',
  templateUrl: './analysis-mail-view.html',
})
export class AnalysisMailView {
  @Input() annotated?: MailMessageExtraFieldsDTO;
}
