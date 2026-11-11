import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { I18nService } from '../core/i18n/i18n.service';
import { CostVsQuotePage } from './cost-vs-quote-page';
import { KpisPage } from './kpis-page';
import { ProductivityPage } from './productivity-page';
import { ReportPeriod } from './report-state';
import { REPORT_ROUTES } from './reporting.routes';
import type { CostVsQuoteReport, KpiReport, ProductivityReport } from './reporting.models';

const MECHANICS = [
  { id: 'm1', tenantId: 't', name: 'Rui', email: 'rui@x.test', role: 'MECHANIC', active: true, version: 0 },
  { id: 'o1', tenantId: 't', name: 'Owner', email: 'o@x.test', role: 'OWNER', active: true, version: 0 },
];

const quoted = {
  workOrderId: 'w1', number: 42, plate: 'AA00AA', deliveredAt: '2026-11-05T14:30:00Z', mechanicId: 'm1', mechanicName: 'Rui',
  quote: { id: 'q1', number: 7, approvedAt: '2026-11-02T10:00:00Z', channel: 'CUSTOMER' as const, netCents: 10000, totalCents: 12300 },
  finalNetCents: 10500, finalVatCents: 2415, finalTotalCents: 12915, differenceCents: 615, differencePercent: 5,
  standardHours: 2.5, trackedHours: 2.75, partsCostCents: 3000, labourCostCents: 2200, marginCents: 5300, marginPercent: 50.48,
};
const unquoted = {
  ...quoted, workOrderId: 'w2', number: 43, plate: 'BB11BB', mechanicId: null, mechanicName: null, quote: null,
  differenceCents: null, differencePercent: null, labourCostCents: null, marginPercent: null,
};

const costReport = (over: Partial<CostVsQuoteReport> = {}): CostVsQuoteReport => ({
  from: '2026-11-01', to: '2026-11-10', timezone: 'Europe/Lisbon', labourCostSet: true, marginBasis: 'PARTS_AND_LABOUR',
  summary: {
    orders: 2, ordersWithApprovedQuote: 1, quoteTotalCents: 12300, finalTotalCents: 12915, differenceCents: 615, differencePercent: 5,
    standardHours: 5, trackedHours: 5.5, netCents: 21000, partsCostCents: 6000, labourCostCents: 2200, marginCents: 10600, marginPercent: 50.48,
  },
  page: 0, size: 50, totalElements: 2, totalPages: 1, items: [quoted, unquoted], ...over,
});

const productivity = (): ProductivityReport => ({
  from: '2026-11-01', to: '2026-11-10', timezone: 'Europe/Lisbon',
  mechanics: [
    { mechanicId: 'm1', name: 'Rui', active: true, trackedHours: 120.5, jobs: 18, standardHours: 110, trackedHoursOnJobs: 100, efficiency: 1.1, averageHoursInWorkshop: 52.25 },
    { mechanicId: 'm2', name: 'Ana', active: false, trackedHours: 0, jobs: 1, standardHours: 2, trackedHoursOnJobs: 0, efficiency: null, averageHoursInWorkshop: null },
  ],
  totals: { trackedHours: 120.5, jobs: 19, standardHours: 112, trackedHoursOnJobs: 100, efficiency: 1.12, averageHoursInWorkshop: 50 },
});

const kpis = (over: Partial<KpiReport> = {}): KpiReport => ({
  from: '2026-11-01', to: '2026-11-10', timezone: 'Europe/Lisbon', labourCostSet: true, retentionMonths: 12, deliveredOrders: 40,
  averageTimePerJob: {
    available: true, trackedHours: 80, ordersWithTime: 32, hoursPerJob: 2.5,
    byService: [{ serviceItemId: 's1', name: 'Mudança de óleo', trackedHours: 10, orders: 20, hoursPerJob: 0.5 }],
  },
  averageOrderValue: {
    available: true, source: 'ISSUED_INVOICES_MINUS_CREDIT_NOTES', invoicedOrders: 30, creditedOrders: 1, netCents: 300000, totalCents: 369000,
    averageNetCents: 10000, averageTotalCents: 12300, notYetInvoiced: { orders: 9, netCents: 80000, totalCents: 98400 },
  },
  margin: {
    available: true, basis: 'PARTS_AND_LABOUR', labourCostSet: true, orders: 40, netCents: 380000, partsCostCents: 120000, labourCostCents: 80000,
    marginCents: 180000, marginPercent: 47.37,
    packageParts: { lines: 12, netCents: 30000, partsCostCents: 18000, marginCents: 12000, marginPercent: 40 },
    otherParts: { netCents: 90000, partsCostCents: 102000, marginCents: -12000, marginPercent: -13.33 },
    byService: [{ serviceItemId: 's1', name: 'Mudança de óleo', lines: 20, orders: 20, netCents: 80000, trackedHours: 10, labourCostCents: 5000, marginCents: 75000, marginPercent: 93.75 }],
  },
  retention: { available: true, months: 12, customers: 35, retained: 21, ratePercent: 60, final: true, monthsCovered: 12 },
  comebacks: { available: true, days: 30, orders: 3, ratePercent: 7.5 },
  occupancy: { available: false, availableWith: 'plan-12-bays' },
  satisfaction: { available: false, availableWith: 'satisfaction' },
  complaints: { available: false, availableWith: 'complaints' },
  ...over,
});

const text = (f: ComponentFixture<unknown>, sel: string) => (f.nativeElement.querySelector(sel) as HTMLElement | null)?.textContent?.replace(/\s+/g, ' ').trim();
const all = (f: ComponentFixture<unknown>, sel: string) => [...f.nativeElement.querySelectorAll(sel)] as HTMLElement[];

function setup<T>(component: new () => T, lang: 'en' | 'pt-PT' | 'pt-BR' = 'en') {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ReportPeriod] });
  TestBed.inject(I18nService).use(lang);
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  const report = (path: string) => http.expectOne((r) => r.url === `/api/v1/reports/${path}` && r.params.get('format') === null);
  const users = () => http.expectOne('/api/v1/users');
  return { http, fixture, report, users, period: TestBed.inject(ReportPeriod) };
}

describe('Cost vs quote page', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads the default period with page 0 and 50 rows and offers the mechanics only', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    const req = report('cost-vs-quote');
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(req.request.params.get('size')).toBe('50');
    req.flush(costReport());
    users().flush(MECHANICS);
    fixture.detectChanges();
    expect(all(fixture, '#cvq-mechanic option').map((o) => o.textContent?.trim())).toEqual(['All mechanics', 'Rui']);
  });

  it('shows the summary, the rows and an em dash where a value is null', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport());
    users().flush([]);
    fixture.detectChanges();

    expect(text(fixture, '[data-test="sum-orders"]')).toBe('2');
    expect(text(fixture, '[data-test="sum-diff"]')).toBe('+€6.15');
    expect(text(fixture, '[data-test="sum-margin"]')).toBe('€106.00');
    expect(text(fixture, '[data-test="sum-margin-percent"]')).toBe('50.48 %');
    expect(text(fixture, '[data-test="scope"]')).toContain('Period 2026-11-01 to 2026-11-10 · time zone Europe/Lisbon');
    expect(text(fixture, '[data-test="scope"]')).toContain('Margin counts parts and labour');

    const rows = all(fixture, '[data-test="row"]');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('#42');
    expect(rows[0].textContent).toContain('Quote 7');
    expect(rows[0].querySelector('[data-test="difference"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('+€6.15+5.00 %');
    // The order without an approved quote: no difference, no mechanic, no labour cost.
    expect(rows[1].querySelector('[data-test="no-quote"]')?.textContent).toBe('No approved quote');
    expect(rows[1].querySelector('[data-test="difference"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('——');
    expect(rows[1].querySelector('[data-test="labour"]')?.textContent?.trim()).toBe('—');
    expect(fixture.nativeElement.querySelector('[data-test="parts-only"]')).toBeNull();
  });

  it('names the parts-only basis and links to the settings when the labour cost is not set', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport({ labourCostSet: false, marginBasis: 'PARTS_ONLY' }));
    users().flush([]);
    fixture.detectChanges();
    expect(text(fixture, '[data-test="scope"]')).toContain('Margin counts parts only');
    const hint = fixture.nativeElement.querySelector('[data-test="parts-only"]') as HTMLElement;
    expect(hint.textContent).toContain('Parts only');
    expect(hint.querySelector('a')?.getAttribute('href')).toBe('/backoffice/settings');
  });

  it('shows an empty state for a period without orders', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport({ items: [], totalElements: 0, totalPages: 0 }));
    users().flush([]);
    fixture.detectChanges();
    expect(text(fixture, '[data-test="empty"]')).toBe('No delivered orders match this period and these filters.');
    expect(fixture.nativeElement.querySelector('[data-test="range"]')).toBeNull();
  });

  it('sends the mechanic and the quote filter and goes back to the first page', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport({ totalElements: 120, totalPages: 3 }));
    users().flush(MECHANICS);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-test="next"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const second = report('cost-vs-quote');
    expect(second.request.params.get('page')).toBe('1');
    second.flush(costReport({ page: 1, totalElements: 120, totalPages: 3 }));
    fixture.detectChanges();
    expect(text(fixture, '[data-test="range"]')).toBe('51–100 of 120');

    const mechanic = fixture.nativeElement.querySelector('#cvq-mechanic') as HTMLSelectElement;
    mechanic.value = 'm1';
    mechanic.dispatchEvent(new Event('change'));
    const quote = fixture.nativeElement.querySelector('#cvq-quote') as HTMLSelectElement;
    quote.value = 'false';
    quote.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const filtered = report('cost-vs-quote');
    expect(filtered.request.params.get('mechanicId')).toBe('m1');
    expect(filtered.request.params.get('hasApprovedQuote')).toBe('false');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(costReport());
  });

  it('reloads with the period the user applied and never lets an older answer win', () => {
    const { fixture, report, users, period } = setup(CostVsQuotePage);
    const first = report('cost-vs-quote');
    users().flush([]);

    period.set('from', '2026-10-01');
    period.set('to', '2026-10-31');
    expect(period.apply()).toBe(true);
    fixture.detectChanges();
    const second = report('cost-vs-quote');
    expect(second.request.params.get('from')).toBe('2026-10-01');
    expect(second.request.params.get('to')).toBe('2026-10-31');

    second.flush(costReport({ from: '2026-10-01', to: '2026-10-31', summary: { ...costReport().summary, orders: 7 } }));
    first.flush(costReport({ summary: { ...costReport().summary, orders: 99 } }));
    fixture.detectChanges();
    expect(text(fixture, '[data-test="sum-orders"]')).toBe('7');
  });

  it('shows the translated error of the server', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush({ code: 'report-range-too-large', errors: { to: 'too long' } }, { status: 400, statusText: 'Bad Request' });
    users().flush([]);
    fixture.detectChanges();
    expect(text(fixture, 'app-alert')).toBe('The period is longer than 24 months. Choose a shorter period.');
  });

  it('is in Portuguese by default and in Brazilian Portuguese on request', () => {
    const pt = setup(CostVsQuotePage, 'pt-PT');
    pt.report('cost-vs-quote').flush(costReport({ items: [], totalElements: 0 }));
    pt.users().flush([]);
    pt.fixture.detectChanges();
    expect(text(pt.fixture, 'h2')).toBe('Custo vs orçamento');
    expect(text(pt.fixture, '[data-test="empty"]')).toContain('Nenhuma ordem entregue');
    TestBed.inject(I18nService).use('pt-BR');
    pt.fixture.detectChanges();
    expect(text(pt.fixture, 'h2')).toBe('Custo x orçamento');
  });
});

describe('CSV export button', () => {
  let anchors: { download: string; href: string }[];

  beforeEach(() => {
    anchors = [];
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      anchors.push({ download: this.download, href: this.href });
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks();
  });

  const csvRequest = (http: HttpTestingController, path: string) => http.expectOne((r) => r.url === `/api/v1/reports/${path}` && r.params.get('format') === 'csv');
  const click = (f: ComponentFixture<unknown>) => (f.nativeElement.querySelector('[data-test="export"]') as HTMLButtonElement).click();

  it('fetches the CSV with the filters of the screen (without paging) and saves it under the server file name', () => {
    const { http, fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport());
    users().flush(MECHANICS);
    const mechanic = fixture.nativeElement.querySelector('#cvq-mechanic') as HTMLSelectElement;
    fixture.detectChanges();
    mechanic.value = 'm1';
    mechanic.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    report('cost-vs-quote').flush(costReport());

    click(fixture);
    fixture.detectChanges();
    const req: TestRequest = csvRequest(http, 'cost-vs-quote');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('mechanicId')).toBe('m1');
    expect(req.request.params.has('page')).toBe(false);
    expect(req.request.params.has('size')).toBe(false);
    expect((fixture.nativeElement.querySelector('[data-test="export"]') as HTMLButtonElement).disabled).toBe(true);

    req.flush(new Blob(['﻿number;plate']), { headers: { 'Content-Disposition': 'attachment; filename="cost-vs-quote-2026-11-01-2026-11-10.csv"' } });
    fixture.detectChanges();
    expect(anchors).toEqual([{ download: 'cost-vs-quote-2026-11-01-2026-11-10.csv', href: 'blob:csv' }]);
    expect((fixture.nativeElement.querySelector('[data-test="export"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('exports the productivity and the KPI reports', () => {
    const prod = setup(ProductivityPage);
    prod.report('productivity').flush(productivity());
    prod.users().flush([]);
    click(prod.fixture);
    csvRequest(prod.http, 'productivity').flush(new Blob(['x']), { headers: { 'Content-Disposition': 'attachment; filename="productivity-2026-11-01-2026-11-10.csv"' } });
    expect(anchors.at(-1)?.download).toBe('productivity-2026-11-01-2026-11-10.csv');
    TestBed.resetTestingModule();

    const k = setup(KpisPage);
    k.report('kpis').flush(kpis());
    click(k.fixture);
    // No Content-Disposition: the file is named after the report.
    csvRequest(k.http, 'kpis').flush(new Blob(['x']));
    expect(anchors.at(-1)?.download).toBe('kpis.csv');
  });

  it.each([
    ['report-range-too-large', 'The period is longer than 24 months. Choose a shorter period.'],
    ['report-too-many-rows', 'Too many orders in this period for one report. Choose a shorter period.'],
  ])('shows the translated error %s and allows another try', async (code, message) => {
    const { http, fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport());
    users().flush([]);
    click(fixture);
    csvRequest(http, 'cost-vs-quote').flush(new Blob([JSON.stringify({ code, errors: { format: 'x' } })], { type: 'application/problem+json' }), { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(fixture, '[data-test="export-error"]')).toBe(message);
    expect(anchors).toHaveLength(0);
    expect((fixture.nativeElement.querySelector('[data-test="export"]') as HTMLButtonElement).disabled).toBe(false);
    click(fixture);
    csvRequest(http, 'cost-vs-quote').flush(new Blob(['x']));
    fixture.detectChanges();
    expect(anchors).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('[data-test="export-error"]')).toBeNull();
  });
});

describe('Productivity page', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows one row per mechanic and the totals, with an em dash for a ratio that cannot be computed', () => {
    const { fixture, report, users } = setup(ProductivityPage);
    report('productivity').flush(productivity());
    users().flush(MECHANICS);
    fixture.detectChanges();

    const rows = all(fixture, '[data-test="row"]');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Rui');
    expect(rows[0].querySelector('[data-test="efficiency"]')?.textContent?.trim()).toBe('1.10');
    expect(rows[0].querySelector('[data-test="average"]')?.textContent?.trim()).toBe('52.25 h');
    expect(rows[1].textContent).toContain('Inactive');
    expect(rows[1].querySelector('[data-test="efficiency"]')?.textContent?.trim()).toBe('—');
    expect(rows[1].querySelector('[data-test="average"]')?.textContent?.trim()).toBe('—');
    expect(text(fixture, '[data-test="total-efficiency"]')).toBe('1.12');
    expect(text(fixture, '[data-test="scope"]')).toContain('Period 2026-11-01 to 2026-11-10');
  });

  it('filters by mechanic', () => {
    const { fixture, report, users } = setup(ProductivityPage);
    report('productivity').flush(productivity());
    users().flush(MECHANICS);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('#prod-mechanic') as HTMLSelectElement;
    expect(all(fixture, '#prod-mechanic option').map((o) => o.textContent?.trim())).toEqual(['All mechanics', 'Rui']);
    select.value = 'm1';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const req = report('productivity');
    expect(req.request.params.get('mechanicId')).toBe('m1');
    req.flush({ ...productivity(), mechanics: [], totals: { ...productivity().totals, jobs: 0, efficiency: null, averageHoursInWorkshop: null } });
    fixture.detectChanges();
    expect(text(fixture, '[data-test="empty"]')).toContain('No mechanic has tracked time');
    expect(fixture.nativeElement.querySelector('[data-test="totals"]')).toBeNull();
  });
});

describe('KPIs page', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('has one card per KPI with its figures, and names the revenue source and the not yet invoiced orders', () => {
    const { fixture, report } = setup(KpisPage);
    report('kpis').flush(kpis());
    fixture.detectChanges();

    expect(text(fixture, '[data-test="time-value"]')).toBe('2.50 h');
    expect(text(fixture, '[data-test="value-net"]')).toBe('€100.00');
    expect(text(fixture, '[data-test="value-total"]')).toBe('€123.00');
    expect(text(fixture, '[data-test="value-source"]')).toBe('Source: issued invoices minus credit notes');
    expect(text(fixture, '[data-test="not-invoiced"]')).toContain('9 delivered orders without an issued invoice: €800.00 before VAT, €984.00 with VAT.');
    expect(text(fixture, '[data-test="margin-value"]')).toBe('€1,800.00');
    expect(text(fixture, '[data-test="margin-percent"]')).toBe('47.37 %');
    expect(text(fixture, '[data-test="margin-basis"]')).toBe('Counts parts and labour. Parts cost €1,200.00, labour cost €800.00.');
    expect(text(fixture, '[data-test="margin-services"]')).toContain('Mudança de óleo');
    expect(text(fixture, '[data-test="retention-value"]')).toBe('60.00 %');
    expect(text(fixture, '[data-test="kpi-retention"]')).toContain('21 of 35 customers came back within 12 months');
    expect(text(fixture, '[data-test="comebacks-value"]')).toBe('7.50 %');
    expect(fixture.nativeElement.querySelector('[data-test="parts-only"]')).toBeNull();
  });

  it('says "available with" and names the feature for each KPI without data, in every language', () => {
    const { fixture, report } = setup(KpisPage);
    report('kpis').flush(kpis());
    fixture.detectChanges();
    expect(text(fixture, '[data-test="kpi-occupancy"] [data-test="unavailable"]')).toBe('Available with the bays and scheduling feature');
    expect(text(fixture, '[data-test="kpi-satisfaction"] [data-test="unavailable"]')).toBe('Available with customer satisfaction surveys');
    expect(text(fixture, '[data-test="kpi-complaints"] [data-test="unavailable"]')).toBe('Available with complaint handling');

    TestBed.inject(I18nService).use('pt-PT');
    fixture.detectChanges();
    expect(text(fixture, '[data-test="kpi-occupancy"] [data-test="unavailable"]')).toBe('Disponível com a funcionalidade de baias e agenda');
    TestBed.inject(I18nService).use('pt-BR');
    fixture.detectChanges();
    expect(text(fixture, '[data-test="kpi-occupancy"] [data-test="unavailable"]')).toBe('Disponível com o recurso de baias e agenda');
  });

  it('keys on `available`: an unknown feature id is shown as it is, and a KPI that fills in shows its figures', () => {
    const { fixture, report } = setup(KpisPage);
    report('kpis').flush(
      kpis({
        occupancy: { available: true, percent: 63.5, label: 'bays' },
        satisfaction: { available: false, availableWith: 'plan-99' },
        retention: { available: false, availableWith: 'satisfaction' },
      }),
    );
    fixture.detectChanges();
    expect(text(fixture, '[data-test="kpi-occupancy"]')).toContain('percent');
    expect(text(fixture, '[data-test="kpi-occupancy"]')).toContain('63.5');
    expect(fixture.nativeElement.querySelector('[data-test="kpi-occupancy"] [data-test="unavailable"]')).toBeNull();
    expect(text(fixture, '[data-test="kpi-satisfaction"] [data-test="unavailable"]')).toBe('Available with plan-99');
    expect(text(fixture, '[data-test="kpi-retention"] [data-test="unavailable"]')).toBe('Available with customer satisfaction surveys');
  });

  it('shows the parts-only hint with a link to the settings, and em dashes for ratios of an empty period', () => {
    const { fixture, report } = setup(KpisPage);
    report('kpis').flush(
      kpis({
        labourCostSet: false,
        deliveredOrders: 0,
        averageTimePerJob: { available: true, trackedHours: 0, ordersWithTime: 0, hoursPerJob: null, byService: [] },
        averageOrderValue: {
          available: true, source: 'ISSUED_INVOICES_MINUS_CREDIT_NOTES', invoicedOrders: 0, creditedOrders: 0, netCents: 0, totalCents: 0,
          averageNetCents: null, averageTotalCents: null, notYetInvoiced: { orders: 0, netCents: 0, totalCents: 0 },
        },
        margin: { available: true, basis: 'PARTS_ONLY', labourCostSet: false, orders: 0, netCents: 0, partsCostCents: 0, labourCostCents: null, marginCents: 0, marginPercent: null, packageParts: { lines: 0, netCents: 0, partsCostCents: 0, marginCents: 0, marginPercent: null }, otherParts: { netCents: 0, partsCostCents: 0, marginCents: 0, marginPercent: null }, byService: [] },
        retention: { available: true, months: 12, customers: 0, retained: 0, ratePercent: null, final: true, monthsCovered: 12 },
        comebacks: { available: true, days: 30, orders: 0, ratePercent: null },
      }),
    );
    fixture.detectChanges();
    expect(text(fixture, '[data-test="time-value"]')).toBe('—');
    expect(text(fixture, '[data-test="value-net"]')).toBe('—');
    expect(text(fixture, '[data-test="margin-percent"]')).toBe('—');
    expect(text(fixture, '[data-test="margin-basis"]')).toBe('Counts parts only. Parts cost €0.00, labour cost —.');
    expect(text(fixture, '[data-test="retention-value"]')).toBe('—');
    expect(text(fixture, '[data-test="comebacks-value"]')).toBe('—');
    const hint = fixture.nativeElement.querySelector('[data-test="parts-only"]') as HTMLElement;
    expect(hint.querySelector('a')?.getAttribute('href')).toBe('/backoffice/settings');
  });
});

describe('Reports shell and routes', () => {
  async function open(url: string) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'backoffice/reports', children: REPORT_ROUTES }])],
    });
    TestBed.inject(I18nService).use('en');
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return { http, harness, el: harness.routeNativeElement as HTMLElement };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('has the three tabs as sub-routes: cost vs quote first, then productivity and KPIs', async () => {
    const { http, harness, el } = await open('/backoffice/reports');
    http.expectOne((r) => r.url === '/api/v1/reports/cost-vs-quote');
    http.expectOne('/api/v1/users');
    const tabs = [...el.querySelectorAll('nav a')].map((a) => [a.getAttribute('href'), a.textContent?.trim()]);
    expect(tabs).toEqual([
      ['/backoffice/reports', 'Cost vs quote'],
      ['/backoffice/reports/productivity', 'Productivity'],
      ['/backoffice/reports/kpis', 'KPIs'],
    ]);
    expect(el.querySelector('a.active')?.textContent?.trim()).toBe('Cost vs quote');

    await harness.navigateByUrl('/backoffice/reports/kpis');
    http.expectOne((r) => r.url === '/api/v1/reports/kpis');
    expect(el.querySelector('a.active')?.textContent?.trim()).toBe('KPIs');
  });

  it('fills the empty period fields with the dates the server used', async () => {
    const { http, harness, el } = await open('/backoffice/reports/kpis');
    http.expectOne((r) => r.url === '/api/v1/reports/kpis').flush(kpis());
    harness.detectChanges();
    expect((el.querySelector('#rp-from') as HTMLInputElement).value).toBe('2026-11-01');
    expect((el.querySelector('#rp-to') as HTMLInputElement).value).toBe('2026-11-10');
  });

  it('refuses a start after the end without calling the server, and applies a valid period to the open tab', async () => {
    const { http, harness, el } = await open('/backoffice/reports/kpis');
    http.expectOne((r) => r.url === '/api/v1/reports/kpis').flush(kpis());
    harness.detectChanges();

    const type = (id: string, value: string) => {
      const input = el.querySelector(id) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      harness.detectChanges();
    };
    const apply = () => {
      (el.querySelector('[data-test="apply"]') as HTMLButtonElement).click();
      harness.detectChanges();
    };

    type('#rp-from', '2026-11-20');
    expect(el.querySelector('[data-test="period-problem"]')?.textContent?.trim()).toBe('The start date is after the end date.');
    apply();
    http.expectNone((r) => r.url === '/api/v1/reports/kpis');

    type('#rp-from', '2026-10-01');
    expect(el.querySelector('[data-test="period-problem"]')).toBeNull();
    apply();
    const req = http.expectOne((r) => r.url === '/api/v1/reports/kpis');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-11-10');
    req.flush(kpis({ from: '2026-10-01' }));
  });
});

describe('Report follow-ups: provisional retention, parts rows, busy server, hints', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('says provisional, N of M months, only while the retention window is not over', () => {
    const { fixture, report } = setup(KpisPage);
    report('kpis').flush(kpis());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-test="provisional"]')).toBeNull();

    TestBed.resetTestingModule();
    const open = setup(KpisPage);
    open.report('kpis').flush(kpis({ retention: { available: true, months: 12, customers: 10, retained: 1, ratePercent: 10, final: false, monthsCovered: 3 } }));
    open.fixture.detectChanges();
    expect(text(open.fixture, '[data-test="provisional"]')).toBe('Provisional, 3 of 12 months');
    TestBed.inject(I18nService).use('pt-PT');
    open.fixture.detectChanges();
    expect(text(open.fixture, '[data-test="provisional"]')).toBe('Provisório, 3 de 12 meses');
    TestBed.inject(I18nService).use('pt-BR');
    open.fixture.detectChanges();
    expect(text(open.fixture, '[data-test="provisional"]')).toBe('Provisório, 3 de 12 meses');
  });

  it('shows package parts and other parts under the per-service margin, with a dash for a null percentage', () => {
    const { fixture, report } = setup(KpisPage);
    const k = kpis();
    if (!k.margin.available) throw new Error('fixture');
    k.margin = { ...k.margin, otherParts: { netCents: 0, partsCostCents: 0, marginCents: 0, marginPercent: null } };
    report('kpis').flush(k);
    fixture.detectChanges();
    const pkg = text(fixture, '[data-test="package-parts"]')!;
    expect(pkg).toContain('Package parts');
    expect(pkg).toContain('€300.00');
    expect(pkg).toContain('€180.00');
    expect(pkg).toContain('40.00 %');
    const other = text(fixture, '[data-test="other-parts"]')!;
    expect(other).toContain('Other parts');
    expect(other.endsWith('€0.00—')).toBe(true);
    expect(text(fixture, '[data-test="kpi-margin"]')).toContain('Both rows are part of the margin above, not additional.');
    expect(text(fixture, '[data-test="kpi-margin"]')).toContain('Labour only.');
  });

  it('explains that standard hours are frozen, on the cost vs quote and the productivity pages', () => {
    const c = setup(CostVsQuotePage);
    c.report('cost-vs-quote').flush(costReport());
    c.users().flush([]);
    c.fixture.detectChanges();
    expect(text(c.fixture, '[data-test="standard-hint"]')).toContain('frozen when a service is added');
    TestBed.resetTestingModule();
    const p = setup(ProductivityPage);
    p.report('productivity').flush(productivity());
    p.users().flush([]);
    p.fixture.detectChanges();
    expect(text(p.fixture, '[data-test="standard-hint"]')).toContain('frozen when a service is added');
  });

  it('keeps the previous figures and offers a retry when the server is busy (429 try-again)', () => {
    const { fixture, report, users, period } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport());
    users().flush([]);
    period.set('from', '2026-10-01');
    period.apply();
    fixture.detectChanges();
    report('cost-vs-quote').flush({ code: 'try-again' }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '5' } });
    fixture.detectChanges();
    expect(text(fixture, '[data-test="busy"]')).toContain('The server is busy with other reports');
    expect(text(fixture, '[data-test="sum-orders"]')).toBe('2');
    expect(all(fixture, '[data-test="row"]')).toHaveLength(2);

    (fixture.nativeElement.querySelector('[data-test="retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const again = report('cost-vs-quote');
    expect(again.request.params.get('from')).toBe('2026-10-01');
    again.flush(costReport({ summary: { ...costReport().summary, orders: 5 } }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-test="busy"]')).toBeNull();
    expect(text(fixture, '[data-test="sum-orders"]')).toBe('5');
  });

  it('shows the busy message on the productivity and KPI pages too', () => {
    const p = setup(ProductivityPage);
    p.report('productivity').flush({ code: 'try-again' }, { status: 429, statusText: 'Too Many Requests' });
    p.users().flush([]);
    p.fixture.detectChanges();
    expect(p.fixture.nativeElement.querySelector('[data-test="retry"]')).not.toBeNull();
    TestBed.resetTestingModule();
    const k = setup(KpisPage);
    k.report('kpis').flush({ code: 'try-again' }, { status: 429, statusText: 'Too Many Requests' });
    k.fixture.detectChanges();
    expect(k.fixture.nativeElement.querySelector('[data-test="retry"]')).not.toBeNull();
  });

  it('translates report-too-many-rows from the JSON list and from the KPIs, not only from the export', () => {
    const c = setup(CostVsQuotePage);
    c.report('cost-vs-quote').flush({ code: 'report-too-many-rows', errors: { format: 'x' } }, { status: 400, statusText: 'Bad Request' });
    c.users().flush([]);
    c.fixture.detectChanges();
    expect(text(c.fixture, '[data-test="error"]')).toBe('Too many orders in this period for one report. Choose a shorter period.');
    expect(c.fixture.nativeElement.querySelector('[data-test="busy"]')).toBeNull();
    TestBed.resetTestingModule();
    const k = setup(KpisPage, 'pt-PT');
    k.report('kpis').flush({ code: 'report-too-many-rows' }, { status: 400, statusText: 'Bad Request' });
    k.fixture.detectChanges();
    expect(text(k.fixture, '[data-test="error"]')).toContain('Demasiadas ordens');
  });
});

describe('CSV export on a busy server', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows the reports busy text on a 429, not the "someone else is changing this" text', () => {
    const { http, fixture, report, users } = setup(CostVsQuotePage);
    report('cost-vs-quote').flush(costReport());
    users().flush([]);
    (fixture.nativeElement.querySelector('[data-test="export"]') as HTMLButtonElement).click();
    http.expectOne((r) => r.params.get('format') === 'csv').flush(new Blob([JSON.stringify({ code: 'try-again' })]), { status: 429, statusText: 'Too Many Requests' });
    return fixture.whenStable().then(() => {
      fixture.detectChanges();
      const shown = text(fixture, '[data-test="export-error"]');
      expect(shown).toContain('The server is busy with other reports');
      expect(shown).not.toContain('Someone else');
    });
  });
});

describe('Cost vs quote page size', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('selects the page size the first request used', () => {
    const { fixture, report, users } = setup(CostVsQuotePage);
    const req = report('cost-vs-quote');
    const requested = req.request.params.get('size');
    req.flush(costReport());
    users().flush([]);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('#cvq-size') as HTMLSelectElement;
    expect(select.value).toBe(requested);
    expect(select.selectedOptions[0].textContent?.trim()).toBe(requested);
  });
});
