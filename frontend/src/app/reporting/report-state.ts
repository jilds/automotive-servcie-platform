import { computed, inject, Injectable, signal } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { ReportingApi } from './reporting-api.service';
import {
  DEFAULT_PERIOD,
  formatCount,
  formatHours,
  formatInstant,
  formatMoney,
  formatNumber,
  formatPercent,
  formatRatio,
  formatSignedMoney,
  isBusy,
  formatSignedPercent,
  PeriodDraft,
  periodProblem,
  saveBlob,
  shownPeriod,
} from './reporting-logic';
import type { ReportKind } from './reporting.models';

/**
 * The period shared by the three report tabs. Provided by the reports shell, so every tab sees the same one.
 * `draft` is what the date inputs hold, `applied` what the tabs load with ('' = the server's default: the first of the
 * month and today). `adopt` shows the dates the server used when the user has not typed any.
 */
@Injectable()
export class ReportPeriod {
  readonly draft = signal<PeriodDraft>({ ...DEFAULT_PERIOD });
  readonly applied = signal<PeriodDraft>({ ...DEFAULT_PERIOD });
  /** The dates of the last answer (echoed by the server). */
  private readonly echoed = signal<PeriodDraft>({ ...DEFAULT_PERIOD });

  /** What the inputs show: the draft, and the server's dates for an empty field. */
  readonly shown = computed(() => shownPeriod(this.draft(), this.echoed()));
  readonly problem = computed(() => periodProblem(this.draft()));

  set(field: keyof PeriodDraft, value: string): void {
    // A field the user has not touched shows the server's date; once one is edited the other keeps what is shown.
    const shown = this.shown();
    this.draft.set({ ...shown, [field]: value });
  }

  /** Applies the draft; returns false (and applies nothing) when it is malformed. */
  apply(): boolean {
    if (this.problem()) return false;
    this.applied.set({ ...this.draft() });
    return true;
  }

  reset(): void {
    this.draft.set({ ...DEFAULT_PERIOD });
    this.applied.set({ ...DEFAULT_PERIOD });
  }

  adopt(from: string, to: string): void {
    this.echoed.set({ from, to });
  }
}

/** Number, money and date formats in the UI language for the report screens; reading `lang` keeps OnPush views current. */
@Injectable({ providedIn: 'root' })
export class ReportFormat {
  private readonly i18n = inject(I18nService);
  private get lang(): string {
    return this.i18n.lang();
  }
  readonly money = (v: number | null | undefined) => formatMoney(v, this.lang);
  readonly signedMoney = (v: number | null | undefined) => formatSignedMoney(v, this.lang);
  readonly percent = (v: number | null | undefined) => formatPercent(v, this.lang);
  readonly signedPercent = (v: number | null | undefined) => formatSignedPercent(v, this.lang);
  readonly hours = (v: number | null | undefined) => formatHours(v, this.lang);
  readonly ratio = (v: number | null | undefined) => formatRatio(v, this.lang);
  readonly count = (v: number | null | undefined) => formatCount(v, this.lang);
  readonly number = (v: number | null | undefined, digits = 2) => formatNumber(v, this.lang, digits);
  readonly instant = (iso: string | null | undefined, zone: string | null | undefined) => formatInstant(iso, zone, this.lang);
}

/** Download state of a report's CSV button: one request at a time, a translated error on failure. */
@Injectable()
export class CsvExport {
  private readonly api = inject(ReportingApi);
  private readonly i18n = inject(I18nService);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  run(kind: ReportKind, query: Parameters<ReportingApi['csv']>[1]): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.csv(kind, query).subscribe({
      next: (file) => {
        saveBlob(file.blob, file.fileName);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(isBusy(e) ? this.i18n.t('reports.busy') : this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }
}
