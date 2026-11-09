import { EditorItem, MAX_TEMPLATE_ITEMS, moveItem, templateChanged, toEditorItems, toTemplateItems, validateTemplate } from './checklist-template-logic';

const items = (...labels: string[]): EditorItem[] => labels.map((label, n) => ({ key: n + 1, label, required: true }));

describe('checklist template editor logic', () => {
  it('accepts 1 to 30 items with a label of at most 120 characters', () => {
    expect(validateTemplate(items('Chaves')).valid).toBe(true);
    expect(validateTemplate(items(...Array.from({ length: MAX_TEMPLATE_ITEMS }, (_, n) => `Item ${n}`))).valid).toBe(true);
    expect(validateTemplate(items('x'.repeat(120))).valid).toBe(true);
  });

  it('rejects an empty list, more than 30 items, blank and over-long labels', () => {
    expect(validateTemplate([])).toMatchObject({ list: 'empty', valid: false });
    expect(validateTemplate(items(...Array.from({ length: 31 }, (_, n) => `Item ${n}`)))).toMatchObject({ list: 'too-many', valid: false });
    expect(validateTemplate(items('ok', '   ', 'x'.repeat(121)))).toMatchObject({ items: { 2: 'blank', 3: 'too-long' }, valid: false });
  });

  it('moves an item up or down and leaves the ends alone', () => {
    expect(moveItem(['a', 'b', 'c'], 1, -1)).toEqual(['b', 'a', 'c']);
    expect(moveItem(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'c', 'b']);
    expect(moveItem(['a', 'b', 'c'], 0, -1)).toEqual(['a', 'b', 'c']);
    expect(moveItem(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'b', 'c']);
    expect(moveItem(['a'], 5, 1)).toEqual(['a']);
  });

  it('builds the PUT items trimmed and in order, and numbers editor keys from a start', () => {
    const editor = toEditorItems([{ label: 'A', required: true }, { label: 'B', required: false }], 10);
    expect(editor.map((i) => i.key)).toEqual([10, 11]);
    expect(toTemplateItems([{ key: 1, label: '  A  ', required: false }])).toEqual([{ label: 'A', required: false }]);
  });

  it('notices a change in text, flag, count or order', () => {
    const saved = [{ label: 'A', required: true }, { label: 'B', required: false }];
    expect(templateChanged(saved, toEditorItems(saved))).toBe(false);
    expect(templateChanged(saved, [{ key: 1, label: ' A ', required: true }, { key: 2, label: 'B', required: false }])).toBe(false);
    expect(templateChanged(saved, [{ key: 1, label: 'A', required: false }, { key: 2, label: 'B', required: false }])).toBe(true);
    expect(templateChanged(saved, toEditorItems(saved.slice(0, 1)))).toBe(true);
    expect(templateChanged(saved, moveItem(toEditorItems(saved), 0, 1))).toBe(true);
  });
});
