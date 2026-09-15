import { Component, Input } from '@angular/core';
import { MailMessageExtraFieldsDTO } from '../../rest';

@Component({
  selector: 'app-work-development-mail-view',
  templateUrl: './work-development-mail-view.html',
})
export class WorkDevelopmentMailView {
  @Input() annotated?: MailMessageExtraFieldsDTO;
}
