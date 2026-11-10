import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api/api.service';
import { problemOf } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { MoneyPipe } from '../shared/money.pipe';
import { BillingApi } from './billing-api.service';
import { closeDialog, newRequestId, openDialog, REQUEST_ID_REUSED } from './billing-logic';
import { BillingSettings, IssueResult } from './billing.models';
import { CustomerFields, CustomerMode, customerErrors, DATA_CODES, EMPTY_CUSTOMER, NAME_MAX, serverFields, toIssueRequest } from './issue-form';

const MODES: readonly CustomerMode[] = ['owner', 'another', 'final'];

/**
 * "Issue invoice" for a delivered work order (owner, front desk). One `requestId` is made each time the dialog
 * opens and reused when the same attempt is sent again (a timeout, a lost answer), so the server never invoices the
 * order twice. The answer (201 issued, 200 replay, 202 stored as PENDING or FAILED) goes to the panel, which closes
 * the dialog. Not enough customer data for the amount (`customer-nif-required`, `customer-address-required`) opens the
 * form for another customer's data and links to the vehicle owner's record.
 */
@Component({
  selector: 'app-invoice-issue-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, MoneyPipe, Alert],
  template: `
    <dialog #dialog class="modal" aria-labelledby="iid-title">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <form class="modal-content" novalidate (submit)="$event.preventDefault(); submit()">
          <div class="modal-header">
            <h2 class="modal-title h5" id="iid-title">{{ 'billing.issue.title' | t: { number: workOrderNumber() } }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (settings()?.invoicing?.sandbox) {
              <app-alert tone="warning">{{ 'billing.sandbox.banner' | t }}</app-alert>
            }
            <div aria-live="polite">
              @if (error()) {
                <app-alert>{{ error() }}</app-alert>
              }
            </div>
            @if (needsData()) {
              <p class="small" data-test="needs-data">
                {{ 'billing.issue.needsData' | t: { limit: (settings()?.finalConsumerLimitCents ?? 0) | money } }}
                @if (customerId(); as c) {
                  <a [routerLink]="['/customers', c]" (click)="close()" data-test="customer-link">{{ 'billing.issue.editCustomer' | t }}</a>
                }
              </p>
            }

            <fieldset class="mb-3">
              <legend class="h6 mb-2">{{ 'billing.issue.customer' | t }}</legend>
              <div class="d-flex flex-column gap-2">
                @for (m of modes; track m) {
                  <div class="form-check">
                    <input class="form-check-input" type="radio" name="iid-mode" [id]="'iid-mode-' + m" [checked]="mode() === m" (change)="mode.set(m)" />
                    <label class="form-check-label" [for]="'iid-mode-' + m">{{ modeText(m) | t }}</label>
                  </div>
                }
              </div>
              @if (mode() === 'owner') {
                <p class="form-text mb-0">{{ 'billing.issue.ownerHelp' | t }}</p>
              } @else if (mode() === 'final') {
                <p class="form-text mb-0">{{ 'billing.issue.finalHelp' | t: { limit: (settings()?.finalConsumerLimitCents ?? 0) | money } }}</p>
              }
            </fieldset>

            @if (mode() === 'another') {
              <div class="row g-3" data-test="other-fields">
                <div class="col-12">
                  <label class="form-label" for="iid-name">{{ 'billing.issue.name' | t }}</label>
                  <input id="iid-name" class="form-control" autocomplete="off" [attr.maxlength]="nameMax" [value]="fields().name" [class.is-invalid]="!!message('name')" (input)="set('name', $event)" />
                  @if (message('name'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="iid-nif">{{ 'common.nif' | t }}</label>
                  <input id="iid-nif" class="form-control" inputmode="numeric" autocomplete="off" [value]="fields().nif" [class.is-invalid]="!!message('nif')" (input)="set('nif', $event)" />
                  @if (message('nif'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
                <div class="col-12"><h3 class="h6 mb-0">{{ 'billing.issue.address' | t }}</h3><p class="form-text mb-0">{{ 'billing.issue.addressHelp' | t }}</p></div>
                <div class="col-sm-8">
                  <label class="form-label" for="iid-street">{{ 'billing.issue.street' | t }}</label>
                  <input id="iid-street" class="form-control" autocomplete="off" [value]="fields().street" [class.is-invalid]="!!message('street')" (input)="set('street', $event)" />
                  @if (message('street'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
                <div class="col-sm-4">
                  <label class="form-label" for="iid-detail">{{ 'billing.issue.detail' | t }}</label>
                  <input id="iid-detail" class="form-control" autocomplete="off" [value]="fields().detail" (input)="set('detail', $event)" />
                </div>
                <div class="col-6 col-sm-4">
                  <label class="form-label" for="iid-postal">{{ 'billing.issue.postalCode' | t }}</label>
                  <input id="iid-postal" class="form-control" autocomplete="off" [value]="fields().postalCode" [class.is-invalid]="!!message('postalCode')" (input)="set('postalCode', $event)" />
                  @if (message('postalCode'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
                <div class="col-6 col-sm-5">
                  <label class="form-label" for="iid-locality">{{ 'billing.issue.locality' | t }}</label>
                  <input id="iid-locality" class="form-control" autocomplete="off" [value]="fields().locality" [class.is-invalid]="!!message('locality')" (input)="set('locality', $event)" />
                  @if (message('locality'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
                <div class="col-6 col-sm-3">
                  <label class="form-label" for="iid-country">{{ 'billing.issue.country' | t }}</label>
                  <input id="iid-country" class="form-control text-uppercase" maxlength="2" autocomplete="off" placeholder="PT" [value]="fields().country" [class.is-invalid]="!!message('country')" (input)="set('country', $event)" />
                  @if (message('country'); as m) {
                    <div class="invalid-feedback">{{ m | t }}</div>
                  }
                </div>
              </div>
            }
            @if (retrying()) {
              <p class="small text-body-secondary mt-3 mb-0" data-test="safe-retry">{{ 'billing.issue.safeRetry' | t }}</p>
            }
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" data-test="submit" [disabled]="busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'billing.issue.submit' | t }}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class InvoiceIssueDialog {
  private readonly api = inject(BillingApi);
  private readonly workshopApi = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly workOrderId = input.required<string>();
  readonly workOrderNumber = input.required<number>();
  readonly vehicleId = input.required<string>();
  /** The answer of the server: the panel shows it and reloads the order's documents. */
  readonly issued = output<IssueResult>();
  /** The order was invoiced meanwhile (or is not invoiceable): the panel reloads its documents. */
  readonly outdated = output<void>();

  protected readonly modes = MODES;
  protected readonly nameMax = NAME_MAX;
  protected readonly mode = signal<CustomerMode>('owner');
  protected readonly fields = signal<CustomerFields>({ ...EMPTY_CUSTOMER });
  protected readonly settings = signal<BillingSettings | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly needsData = signal(false);
  protected readonly customerId = signal<string | null>(null);
  protected readonly retrying = signal(false);
  private readonly serverMarks = signal<Partial<Record<keyof CustomerFields, string>>>({});
  private readonly clientErrors = computed(() => (this.mode() === 'another' && this.submitted() ? customerErrors(this.fields()) : {}));
  /** Made when the dialog opens; the same value goes out on every send of this attempt. */
  private requestId = newRequestId();

  open(): void {
    this.requestId = newRequestId();
    this.mode.set('owner');
    this.fields.set({ ...EMPTY_CUSTOMER });
    this.error.set(null);
    this.submitted.set(false);
    this.needsData.set(false);
    this.customerId.set(null);
    this.retrying.set(false);
    this.serverMarks.set({});
    this.busy.set(false);
    openDialog(this.dialog().nativeElement);
    this.api.settings().subscribe({ next: (s) => this.settings.set(s), error: () => this.settings.set(null) });
  }

  close(): void {
    closeDialog(this.dialog().nativeElement);
  }

  protected modeText(m: CustomerMode): MessageKey {
    return `billing.issue.mode.${m}`;
  }

  protected set(field: keyof CustomerFields, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.fields.update((f) => ({ ...f, [field]: value }));
    this.serverMarks.update(({ [field]: _gone, ...rest }) => rest);
  }

  /** The message of a field: the server's verdict first, then the form's own check once a send was tried. */
  protected message(field: keyof CustomerFields): MessageKey | null {
    const server = this.serverMarks()[field];
    if (server) {
      const key = `error.${server}` as MessageKey;
      return server !== 'validation' && this.i18n.has(key) ? key : 'error.field';
    }
    return this.clientErrors()[field] ?? null;
  }

  submit(): void {
    if (this.busy()) return;
    this.submitted.set(true);
    this.error.set(null);
    if (this.mode() === 'another' && Object.keys(customerErrors(this.fields())).length > 0) return;
    this.busy.set(true);
    this.api.issue(this.workOrderId(), toIssueRequest(this.requestId, this.mode(), this.fields())).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.close();
        this.issued.emit(result);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problem = problemOf(e);
        const code = problem?.code ?? null;
        this.error.set(this.i18n.error(e));
        this.serverMarks.set(serverFields(code, problem?.errors));
        if (code && DATA_CODES.has(code)) this.askForData();
        // The id belongs to a closed document or another order: the next send is a new attempt with a new id.
        if (code === REQUEST_ID_REUSED) this.requestId = newRequestId();
        // No answer (network, gateway, 5xx): the same attempt can go out again with the same request id.
        this.retrying.set(!problem || problem.status === 0 || problem.status >= 500);
        // The order is not invoiceable any more or already invoiced: the panel reloads.
        if (code === 'already-invoiced' || code === 'work-order-not-invoiceable') this.outdated.emit();
      },
    });
  }

  /** Not enough customer data for the amount: ask for it in the form, and find the owner's record to link to. */
  private askForData(): void {
    this.needsData.set(true);
    this.mode.set('another');
    this.submitted.set(true);
    this.workshopApi.vehicle(this.vehicleId()).subscribe({
      next: (v) => this.customerId.set(v.customerId),
      error: () => this.customerId.set(null),
    });
  }
}
