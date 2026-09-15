import { Component, Input } from '@angular/core';
import { MailMessageDTO } from '../../rest';

@Component({
  selector: 'app-mail-source-view',
  templateUrl: './mail-source-view.html',
})
export class MailSourceView {
  @Input() message?: MailMessageDTO;
}
