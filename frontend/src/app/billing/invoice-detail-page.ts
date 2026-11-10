import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { BillingApi } from './billing-api.service';
import { BillingSettings, Invoice } from './billing.models';
import { InvoiceView } from './invoice-view';
import { PaymentsPanel } from './payments-panel';

/**
 * `/invoices/:id`: one document with its actions and, for an ISSUED invoice or one that was paid, its payments.
 * Loads the billing settings once (methods, sandbox) and reloads the document after every change.
 */
@Component({
  selector: 'app-invoice-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, Alert, InvoiceView, PaymentsPanel],
  template: `
    <a routerLink="/invoices" class="d-inline-block mb-2 small fw-medium text-body-secondary text-decoration-none">{{ 'billing.detail.back' | t }}</a>
    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }
    @if (invoice(); as i) {
      <app-invoice-view [invoice]="i" (changed)="load()" />
      @if (showPayments(i)) {
        <app-payments-panel [invoice]="i" [settings]="settings()" (changed)="load()" />
      }
    } @else if (!error()) {
      <div class="placeholder-glow vstack gap-2" aria-hidden="true">
        <span class="placeholder placeholder-lg col-6"></span>
        <span class="placeholder placeholder-block w-100"></span>
      </div>
    }
  `,
})
export class InvoiceDetailPage {
  private readonly api = inject(BillingApi);
  private readonly i18n = inject(I18nService);

  readonly id = input.required<string>();
  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly settings = signal<BillingSettings | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.id();
      untracked(() => {
        this.invoice.set(null);
        this.load();
      });
    });
    this.api.settings().subscribe({ next: (s) => this.settings.set(s), error: () => this.settings.set(null) });
  }

  load(): void {
    this.api.get(this.id()).subscribe({
      next: (i) => {
        this.error.set(null);
        this.invoice.set(i);
      },
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  /** Payments belong to invoices that are ISSUED (to pay) or that have payment history. */
  protected showPayments(i: Invoice): boolean {
    return i.kind === 'INVOICE' && (i.status === 'ISSUED' || i.payments.length > 0);
  }
}
