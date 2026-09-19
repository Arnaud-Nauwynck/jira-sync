import { MailMessageDTO } from '../rest';

/** Matches the "{yyyy-MM-dd}-{messageId}" shape built by {@link toDisplayId}. */
const DATE_PREFIXED_ID = /^\d{4}-\d{2}-\d{2}-(.+)$/;

/**
 * Builds the same "{yyyy-MM-dd}-{messageId}" display id as the backend's
 * {@code MailMessageDTO#id()} (see fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO):
 * more readable / sortable than the raw Message-ID, and a form the backend can infer the
 * partition month from (see {@code MailMessageRepository#findByMessageId}), avoiding a full scan.
 * Falls back to the raw Message-ID when the date is unknown.
 */
export function toDisplayId(message: Pick<MailMessageDTO, 'messageId' | 'date'>): string {
  const messageId = message.messageId!;
  const day = message.date?.substring(0, 10);
  return day ? `${day}-${messageId}` : messageId;
}

/** Strips the "{yyyy-MM-dd}-" prefix that {@link toDisplayId} adds, if present; returns the value
 * unchanged (a plain Message-ID) otherwise. */
export function extractMessageId(dateMessageIdOrMessageId: string): string {
  const match = DATE_PREFIXED_ID.exec(dateMessageIdOrMessageId);
  return match ? match[1] : dateMessageIdOrMessageId;
}
