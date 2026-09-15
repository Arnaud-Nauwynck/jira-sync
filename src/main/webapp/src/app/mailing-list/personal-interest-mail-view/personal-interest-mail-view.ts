import { Component, Input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MailMessageDTO, MailMessagesService } from '../../rest';

@Component({
  imports: [FormsModule],
  selector: 'app-personal-interest-mail-view',
  templateUrl: './personal-interest-mail-view.html',
})
export class PersonalInterestMailView {
  @Input() message?: MailMessageDTO;

  editing = signal(false);
  saving = signal(false);
  draftComment = '';
  draftPriority10 = 0;

  constructor(private mailMessagesService: MailMessagesService) {}

  toggleEdit() {
    if (this.editing()) {
      this.editing.set(false);
      return;
    }
    this.draftComment = this.message?.annotated?.personalInterrestComment ?? '';
    this.draftPriority10 = this.message?.annotated?.personalInterrestPriority10 ?? 0;
    this.editing.set(true);
  }

  save() {
    const messageId = this.message?.messageId;
    if (!messageId) {
      return;
    }
    this.saving.set(true);
    this.mailMessagesService.putPersonalInterrestComment({
      messageId,
      personalInterrestComment: this.draftComment,
      personalInterrestPriority10: this.draftPriority10,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        if (this.message) {
          this.message.annotated = {
            ...this.message.annotated,
            personalInterrestComment: this.draftComment,
            personalInterrestPriority10: this.draftPriority10,
          };
        }
      },
      error: (ex) => {
        this.saving.set(false);
        console.error('... Failed call putPersonalInterrestComment', ex);
      },
    });
  }
}
