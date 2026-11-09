import { TemplateItem } from '../workshop/floor.models';

/** The API keeps 1 to 30 items of at most 120 characters. */
export const MAX_TEMPLATE_ITEMS = 30;
export const TEMPLATE_LABEL_MAX = 120;

/** An item as edited: `key` is stable while the list is reordered, so the inputs keep their focus. */
export interface EditorItem {
  key: number;
  label: string;
  required: boolean;
}

export type ItemProblem = 'blank' | 'too-long';
export type ListProblem = 'empty' | 'too-many';

export interface TemplateValidation {
  list: ListProblem | null;
  /** Problem per item key. */
  items: Record<number, ItemProblem>;
  valid: boolean;
}

export function toEditorItems(items: readonly TemplateItem[], nextKey = 1): EditorItem[] {
  return items.map((i, n) => ({ key: nextKey + n, label: i.label, required: i.required }));
}

export function validateTemplate(items: readonly EditorItem[]): TemplateValidation {
  const problems: Record<number, ItemProblem> = {};
  for (const i of items) {
    const label = i.label.trim();
    if (!label) problems[i.key] = 'blank';
    else if (label.length > TEMPLATE_LABEL_MAX) problems[i.key] = 'too-long';
  }
  const list: ListProblem | null = items.length === 0 ? 'empty' : items.length > MAX_TEMPLATE_ITEMS ? 'too-many' : null;
  return { list, items: problems, valid: list === null && Object.keys(problems).length === 0 };
}

/** Moves an item up (delta -1) or down (+1); out of range leaves the list as it is. */
export function moveItem<T>(items: readonly T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (index < 0 || index >= items.length || target < 0 || target >= items.length) return [...items];
  const copy = [...items];
  [copy[index], copy[target]] = [copy[target], copy[index]];
  return copy;
}

/** The PUT items: labels trimmed, in order. */
export function toTemplateItems(items: readonly EditorItem[]): TemplateItem[] {
  return items.map((i) => ({ label: i.label.trim(), required: i.required }));
}

/** True when the edited list differs from the saved one (labels compared trimmed). */
export function templateChanged(saved: readonly TemplateItem[], items: readonly EditorItem[]): boolean {
  if (saved.length !== items.length) return true;
  return items.some((i, n) => i.label.trim() !== saved[n].label || i.required !== saved[n].required);
}
