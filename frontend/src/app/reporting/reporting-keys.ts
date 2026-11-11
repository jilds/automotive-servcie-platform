import type { MessageKey } from '../core/i18n/messages.en';
import { isKnownFeature } from './reporting-logic';
import type { MarginBasis } from './reporting.models';

/** The text naming what a margin counts: parts and labour, or parts only (the labour cost is not set). */
export function basisKey(basis: MarginBasis): MessageKey {
  return basis === 'PARTS_ONLY' ? 'reports.basis.PARTS_ONLY' : 'reports.basis.PARTS_AND_LABOUR';
}

/** The text naming the feature a KPI waits for, or null for a feature id this screen does not know (shown as it is). */
export function availableWithKey(id: string): MessageKey | null {
  if (!isKnownFeature(id)) return null;
  return `reports.availableWith.${id}`;
}

/** The text naming where an average order value comes from, or null for a source this screen does not know. */
export function sourceKey(source: string): MessageKey | null {
  return source === 'ISSUED_INVOICES_MINUS_CREDIT_NOTES' ? 'reports.source.ISSUED_INVOICES_MINUS_CREDIT_NOTES' : null;
}
