import { TestBed } from '@angular/core/testing';

import { I18nService } from '../core/i18n/i18n.service';
import { LanguageSelect } from './language-select';

describe('LanguageSelect', () => {
  beforeEach(() => {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
  });

  it('shows PT / EN / BR buttons in compact mode and switches the language', () => {
    const fixture = TestBed.createComponent(LanguageSelect);
    fixture.componentRef.setInput('compact', true);
    fixture.detectChanges();
    const i18n = TestBed.inject(I18nService);

    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(buttons.map((b) => b.querySelector('[aria-hidden]')?.textContent)).toEqual(['PT', 'EN', 'BR']);
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('[role=group]').getAttribute('aria-label')).toBe(i18n.t('language.label'));

    buttons[1].click();
    fixture.detectChanges();
    expect(i18n.lang()).toBe('en');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
  });

  it('keeps the drop-down by default', () => {
    const fixture = TestBed.createComponent(LanguageSelect);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
