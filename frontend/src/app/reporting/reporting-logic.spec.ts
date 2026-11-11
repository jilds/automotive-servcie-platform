import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  approvedQuoteFilter,
  costVsQuoteParams,
  DASH,
  filenameFromDisposition,
  formatHours,
  formatInstant,
  formatMoney,
  formatNumber,
  formatPercent,
  formatRatio,
  formatSignedMoney,
  formatSignedPercent,
  isAvailable,
  isKnownFeature,
  periodParams,
  periodProblem,
  productivityParams,
  REVOKE_DELAY_MS,
  saveBlob,
  shownPeriod,
  unavailable,
} from './reporting-logic';
import { centsToEuros, eurosToCents } from '../shared/money';
import { availableWithKey, basisKey, sourceKey } from './reporting-keys';
import type { Kpi, RetentionKpi } from './reporting.models';

describe('period', () => {
  it('accepts empty fields (the server default) and complete ISO days', () => {
    expect(periodProblem({ from: '', to: '' })).toBeNull();
    expect(periodProblem({ from: '2026-11-01', to: '' })).toBeNull();
    expect(periodProblem({ from: '2026-11-01', to: '2026-11-01' })).toBeNull();
  });

  it('rejects a start after the end and malformed dates', () => {
    expect(periodProblem({ from: '2026-11-02', to: '2026-11-01' })).toBe('from-after-to');
    expect(periodProblem({ from: '01/11/2026', to: '' })).toBe('invalid');
    expect(periodProblem({ from: '', to: '2026-1-1' })).toBe('invalid');
  });

  it('shows the dates the server used for the fields the user has not filled in', () => {
    const echoed = { from: '2026-11-01', to: '2026-11-10' };
    expect(shownPeriod({ from: '', to: '' }, echoed)).toEqual(echoed);
    expect(shownPeriod({ from: '2026-10-15', to: '' }, echoed)).toEqual({ from: '2026-10-15', to: '2026-11-10' });
    expect(shownPeriod({ from: '2026-10-15', to: '2026-10-31' }, echoed)).toEqual({ from: '2026-10-15', to: '2026-10-31' });
  });

  it('leaves empty dates out of the query so the server default applies', () => {
    expect(periodParams({}).keys()).toEqual([]);
    expect(periodParams({ from: '2026-11-01', to: '2026-11-30' }).toString()).toBe('from=2026-11-01&to=2026-11-30');
  });
});

describe('query parameters', () => {
  it('builds the cost vs quote query with paging, and without it for the CSV', () => {
    const q = { from: '2026-11-01', to: '2026-11-30', mechanicId: 'm1', hasApprovedQuote: false, page: 2, size: 25 };
    expect(costVsQuoteParams(q).toString()).toBe('from=2026-11-01&to=2026-11-30&mechanicId=m1&hasApprovedQuote=false&page=2&size=25');
    expect(costVsQuoteParams(q, false).toString()).toBe('from=2026-11-01&to=2026-11-30&mechanicId=m1&hasApprovedQuote=false');
    expect(costVsQuoteParams({}).toString()).toBe('page=0&size=50');
  });

  it('sends hasApprovedQuote only when the filter is set, true and false alike', () => {
    expect(approvedQuoteFilter('')).toBeUndefined();
    expect(approvedQuoteFilter('true')).toBe(true);
    expect(approvedQuoteFilter('false')).toBe(false);
    expect(costVsQuoteParams({ hasApprovedQuote: true }).get('hasApprovedQuote')).toBe('true');
    expect(costVsQuoteParams({}).has('hasApprovedQuote')).toBe(false);
  });

  it('builds the productivity query', () => {
    expect(productivityParams({ mechanicId: 'm1' }).toString()).toBe('mechanicId=m1');
    expect(productivityParams({}).toString()).toBe('');
  });
});

describe('euros and cents of a setting', () => {
  it('converts what is typed to integer cents without float noise', () => {
    expect(eurosToCents(25)).toBe(2500);
    expect(eurosToCents(45.25)).toBe(4525);
    expect(eurosToCents(0.29)).toBe(29);
    expect(eurosToCents(1.15)).toBe(115);
    expect(eurosToCents('12.5')).toBe(1250);
    expect(eurosToCents(0)).toBe(0);
  });

  it('keeps empty as null, never 0', () => {
    expect(eurosToCents(null)).toBeNull();
    expect(eurosToCents(undefined)).toBeNull();
    expect(eurosToCents('')).toBeNull();
    expect(eurosToCents('abc')).toBeNull();
  });

  it('shows cents as euros and null as empty', () => {
    expect(centsToEuros(2500)).toBe(25);
    expect(centsToEuros(0)).toBe(0);
    expect(centsToEuros(null)).toBeNull();
  });
});

describe('formatting a value the report could not compute', () => {
  it('shows an em dash for null and undefined, never 0', () => {
    expect(DASH).toBe('—');
    for (const f of [formatNumber, formatPercent, formatHours, formatRatio, formatMoney, formatSignedMoney, formatSignedPercent]) {
      expect(f(null, 'en')).toBe('—');
      expect(f(undefined, 'pt-PT')).toBe('—');
    }
    expect(formatInstant(null, 'Europe/Lisbon', 'en')).toBe('—');
    expect(formatInstant('not a date', 'Europe/Lisbon', 'en')).toBe('—');
  });

  it('formats a real zero as a number', () => {
    expect(formatPercent(0, 'en')).toBe('0.00 %');
    expect(formatHours(0, 'en')).toBe('0.00 h');
    expect(formatRatio(0, 'en')).toBe('0.00');
  });

  it('uses the UI language: decimal comma in Portuguese, point in English', () => {
    expect(formatPercent(12.5, 'pt-PT')).toBe('12,50 %');
    expect(formatPercent(12.5, 'en')).toBe('12.50 %');
    expect(formatHours(2.75, 'pt-BR')).toBe('2,75 h');
  });

  it('shows money in euros and signs a difference', () => {
    expect(formatMoney(12345, 'en')).toBe('€123.45');
    expect(formatSignedMoney(615, 'en')).toBe('+€6.15');
    expect(formatSignedMoney(-615, 'en')).toBe('-€6.15');
    expect(formatSignedMoney(0, 'en')).toBe('€0.00');
    expect(formatSignedPercent(5, 'en')).toBe('+5.00 %');
    expect(formatSignedPercent(-5, 'en')).toBe('-5.00 %');
  });

  it('shows an instant in the workshop time zone, across the clock change', () => {
    // 23:30 UTC is 23:30 in Lisbon in winter and 00:30 the next day in summer (WEST, UTC+1).
    expect(formatInstant('2026-01-10T23:30:00Z', 'Europe/Lisbon', 'en-GB')).toBe('10/01/2026, 23:30');
    expect(formatInstant('2026-07-10T23:30:00Z', 'Europe/Lisbon', 'en-GB')).toBe('11/07/2026, 00:30');
    expect(formatInstant('2026-07-10T23:30:00Z', 'Not/AZone', 'en-GB')).not.toBe('—');
  });
});

describe('Content-Disposition file name', () => {
  const fallback = 'kpis.csv';

  it('reads the quoted name', () => {
    expect(filenameFromDisposition('attachment; filename="cost-vs-quote-2026-11-01-2026-11-30.csv"', fallback)).toBe('cost-vs-quote-2026-11-01-2026-11-30.csv');
  });

  it('reads an unquoted name and the extended form', () => {
    expect(filenameFromDisposition('attachment; filename=productivity-2026-11-01-2026-11-30.csv', fallback)).toBe('productivity-2026-11-01-2026-11-30.csv');
    expect(filenameFromDisposition("attachment; filename*=UTF-8''kpis%20nov.csv", fallback)).toBe('kpis nov.csv');
  });

  it('falls back when the header is missing or holds nothing usable', () => {
    expect(filenameFromDisposition(null, fallback)).toBe(fallback);
    expect(filenameFromDisposition('attachment', fallback)).toBe(fallback);
    expect(filenameFromDisposition('attachment; filename=""', fallback)).toBe(fallback);
  });

  it('drops path parts so the file stays in the downloads folder', () => {
    expect(filenameFromDisposition('attachment; filename="../../etc/kpis.csv"', fallback)).toBe('kpis.csv');
    expect(filenameFromDisposition('attachment; filename="C:\\\\temp\\\\kpis.csv"', fallback)).toBe('kpis.csv');
  });
});

describe('KPI availability', () => {
  const retention: Kpi<RetentionKpi> = { available: true, months: 12, customers: 5, retained: 3, ratePercent: 60, final: true, monthsCovered: 12 };
  const waiting: Kpi<RetentionKpi> = { available: false, availableWith: 'satisfaction' };

  it('keys on `available`, never on the feature id', () => {
    expect(isAvailable(retention)).toBe(true);
    expect(isAvailable(waiting)).toBe(false);
    expect(unavailable(retention)).toBeNull();
    expect(unavailable(waiting)?.availableWith).toBe('satisfaction');
  });

  it('names the three known features and leaves unknown ones as they come', () => {
    expect(['plan-12-bays', 'satisfaction', 'complaints'].every(isKnownFeature)).toBe(true);
    expect(isKnownFeature('plan-99')).toBe(false);
    expect(availableWithKey('plan-12-bays')).toBe('reports.availableWith.plan-12-bays');
    expect(availableWithKey('plan-99')).toBeNull();
  });

  it('names the margin basis and the revenue source', () => {
    expect(basisKey('PARTS_ONLY')).toBe('reports.basis.PARTS_ONLY');
    expect(basisKey('PARTS_AND_LABOUR')).toBe('reports.basis.PARTS_AND_LABOUR');
    expect(sourceKey('ISSUED_INVOICES_MINUS_CREDIT_NOTES')).toBe('reports.source.ISSUED_INVOICES_MINUS_CREDIT_NOTES');
    expect(sourceKey('SOMETHING_ELSE')).toBeNull();
  });
});

describe('saveBlob', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('downloads under the given name and revokes the object URL afterwards', () => {
    vi.useFakeTimers();
    const create = vi.fn(() => 'blob:report');
    const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    let downloaded: { href: string; download: string } | null = null;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloaded = { href: this.href, download: this.download };
    });

    saveBlob(new Blob(['a;b']), 'kpis-2026-11-01-2026-11-30.csv');

    expect(downloaded).toEqual({ href: 'blob:report', download: 'kpis-2026-11-01-2026-11-30.csv' });
    expect(document.querySelector('a[download]')).toBeNull();
    expect(revoke).not.toHaveBeenCalled();
    vi.advanceTimersByTime(REVOKE_DELAY_MS);
    expect(revoke).toHaveBeenCalledWith('blob:report');
  });
});

describe('money formatting is cached per language', () => {
  it('builds one currency formatter per language and reuses it', () => {
    const spy = vi.spyOn(Intl, 'NumberFormat');
    const before = spy.mock.calls.length;
    formatMoney(100, 'es-ES');
    formatMoney(250, 'es-ES');
    formatSignedMoney(5, 'es-ES');
    expect(spy.mock.calls.length - before).toBe(1);
    formatMoney(100, 'nl-NL');
    expect(spy.mock.calls.length - before).toBe(2);
    spy.mockRestore();
  });
});
