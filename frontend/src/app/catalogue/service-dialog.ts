import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';

import { applyProblem, fieldMessage, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { CatalogueApi } from './catalogue-api.service';
import { CatalogueService } from './catalogue.models';
import {
  copyServiceValue,
  hourlyPriceCents,
  newServiceValue,
  SERVICE_FIELDS,
  serviceForm,
  ServiceForm,
  serviceFormValue,
  toServiceRequest,
} from './catalogue-forms';

type Field = keyof ServiceForm['controls'];

/**
 * Creates or edits a catalogue service. Open with `open(service)` to edit, `open(null)` for a new one or
 * `open(service, true)` for a copy saved as a new service. An hourly service shows its price from the labour rate.
 */
@Component({
  selector: 'app-service-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="service-dialog-title">
      <div class="modal-dialog modal-lg">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="service-dialog-title">{{ (editing() ? 'catalogue.editService' : copying() ? 'catalogue.copyService' : 'catalogue.newService') | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert class="mb-3">{{ error() }}</app-alert>
            }
            <div class="row g-3">
              <div class="col-sm-8">
                <label class="form-label" for="sd-name">{{ 'catalogue.name' | t }}</label>
                <input id="sd-name" class="form-control" formControlName="name" maxlength="120" [class.is-invalid]="!!message('name')" />
                @if (message('name'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-4">
                <label class="form-label" for="sd-category">{{ 'catalogue.category' | t }}</label>
                <input id="sd-category" class="form-control" formControlName="category" maxlength="60" [placeholder]="'catalogue.categoryHint' | t" />
              </div>
              <div class="col-12">
                <label class="form-label" for="sd-description">{{ 'catalogue.description' | t }}</label>
                <textarea id="sd-description" class="form-control" rows="2" formControlName="description" maxlength="1000"></textarea>
              </div>
              <div class="col-12">
                <span class="form-label d-block" id="sd-pricing-label">{{ 'catalogue.pricing.label' | t }}</span>
                <div class="btn-group flex-wrap" role="radiogroup" aria-labelledby="sd-pricing-label">
                  <input type="radio" class="btn-check" id="sd-pricing-fixed" value="FIXED" formControlName="pricing" />
                  <label class="btn btn-outline-primary" for="sd-pricing-fixed">{{ 'catalogue.pricing.FIXED' | t }}</label>
                  <input type="radio" class="btn-check" id="sd-pricing-hourly" value="HOURLY" formControlName="pricing" />
                  <label class="btn btn-outline-primary" for="sd-pricing-hourly">{{ 'catalogue.pricing.HOURLY' | t }}</label>
                </div>
                @if (hourly()) {
                  <div class="form-text" id="sd-hourly-price" aria-live="polite">
                    @if (labourRateCents() !== null) {
                      <strong class="text-body">{{ 'catalogue.pricing.formula' | t: { hours: hoursText(), rate: (labourRateCents() | money), price: (hourlyCents() | money) } }}</strong>
                      · {{ 'catalogue.pricing.rateHelp' | t }}
                    } @else {
                      {{ 'catalogue.pricing.rateUnknown' | t }}
                    }
                  </div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="sd-price">{{ 'catalogue.price' | t }}</label>
                <input id="sd-price" class="form-control" type="number" min="0" step="0.01" formControlName="priceEuros" [class.is-invalid]="!!message('priceEuros')" [attr.aria-describedby]="hourly() ? 'sd-hourly-price' : null" />
                @if (message('priceEuros'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="sd-vat">{{ 'catalogue.vat' | t }}</label>
                <input id="sd-vat" class="form-control" type="number" min="0" max="100" step="0.01" formControlName="vatRate" [class.is-invalid]="!!message('vatRate')" />
                @if (message('vatRate'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="sd-hours">{{ 'catalogue.standardHours' | t }}</label>
                <input id="sd-hours" class="form-control" type="number" min="0" step="0.25" formControlName="standardHours" [class.is-invalid]="!!message('standardHours')" aria-describedby="sd-hours-help" />
                @if (message('standardHours'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="sd-duration">{{ 'catalogue.duration' | t }}</label>
                <input id="sd-duration" class="form-control" type="number" min="5" max="1440" step="5" formControlName="durationMinutes" [class.is-invalid]="!!message('durationMinutes')" aria-describedby="sd-duration-help" />
                @if (message('durationMinutes'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-12 form-text mt-1" id="sd-hours-help">{{ 'catalogue.standardHoursHelp' | t }} <span id="sd-duration-help">{{ 'catalogue.durationHelp' | t }}</span></div>
              <div class="col-12">
                <div class="form-check">
                  <input id="sd-active" class="form-check-input" type="checkbox" formControlName="active" />
                  <label class="form-check-label" for="sd-active">{{ 'catalogue.activeLabel' | t }}</label>
                </div>
                <div class="form-check mt-2">
                  <input id="sd-online" class="form-check-input" type="checkbox" formControlName="listedOnline" aria-describedby="sd-online-help" />
                  <label class="form-check-label" for="sd-online">{{ 'catalogue.listedOnline' | t }}</label>
                  <div class="form-text" id="sd-online-help">{{ 'catalogue.listedOnlineHelp' | t }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'common.save' | t }}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class ServiceDialog {
  /** The workshop's hourly labour rate in cents, to show the price of an hourly service; null when unknown. */
  readonly labourRateCents = input<number | null>(null);
  /** Emits the service as saved by the server. */
  readonly saved = output<CatalogueService>();
  private readonly api = inject(CatalogueApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly form = serviceForm();
  protected readonly editing = signal<CatalogueService | null>(null);
  /** True while the dialog holds a copy of a service, to be saved as a new one. */
  protected readonly copying = signal(false);
  private readonly pricing = toSignal(this.form.controls.pricing.valueChanges, { initialValue: this.form.controls.pricing.value });
  private readonly hoursValue = toSignal(this.form.controls.standardHours.valueChanges, {
    initialValue: this.form.controls.standardHours.value,
  });
  protected readonly hourly = computed(() => this.pricing() === 'HOURLY');
  protected readonly hourlyCents = computed(() => hourlyPriceCents(this.hoursValue(), this.labourRateCents() ?? 0));
  protected readonly hoursText = computed(() =>
    new Intl.NumberFormat(this.i18n.lang(), { maximumFractionDigits: 2 }).format(Number(this.hoursValue() ?? 0)),
  );
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // The fixed price does not apply to an hourly service: keep it (sent as stored) but lock the field.
    this.form.controls.pricing.valueChanges.pipe(takeUntilDestroyed()).subscribe((pricing) => {
      const price = this.form.controls.priceEuros;
      if (pricing === 'HOURLY') price.disable({ emitEvent: false });
      else price.enable({ emitEvent: false });
    });
  }

  /** @param copy fill the dialog with a copy of `service`, saved as a new service */
  open(service: CatalogueService | null, copy = false): void {
    this.error.set(null);
    this.busy.set(false);
    if (service && copy) this.fillCopy(service);
    else this.fill(service);
    const d = this.dialog().nativeElement;
    if (typeof d.showModal === 'function') d.showModal();
    else d.setAttribute('open', '');
  }

  close(): void {
    const d = this.dialog().nativeElement;
    if (typeof d.close === 'function') d.close();
    else d.removeAttribute('open');
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const current = this.editing();
    const call = current
      ? this.api.updateService(current.id, toServiceRequest(this.form, current.version))
      : this.api.createService(toServiceRequest(this.form));
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (s) => {
        this.busy.set(false);
        this.saved.emit(s);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        if (current && isStale(e)) {
          // Saved elsewhere since this dialog opened: show the stored service, then say why.
          this.api.service(current.id).subscribe({ next: (s) => this.fill(s), error: () => {} });
          this.error.set(this.i18n.error(e));
          return;
        }
        const marked = applyProblem(this.form, e, {}, SERVICE_FIELDS);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected message(field: Field): MessageKey | null {
    return fieldMessage(this.form.controls[field], {
      required: field === 'name' ? 'catalogue.required' : MESSAGES[field],
      min: MESSAGES[field],
      max: MESSAGES[field],
      decimals: MESSAGES[field],
    });
  }

  private fill(service: CatalogueService | null): void {
    this.editing.set(service);
    this.copying.set(false);
    this.form.reset(service ? serviceFormValue(service) : newServiceValue());
  }

  private fillCopy(service: CatalogueService): void {
    this.editing.set(null);
    this.copying.set(true);
    this.form.reset(copyServiceValue(service, this.i18n.t('catalogue.copySuffix')));
  }
}

const MESSAGES: Partial<Record<Field, MessageKey>> = {
  priceEuros: 'catalogue.amountInvalid',
  vatRate: 'catalogue.vatInvalid',
  standardHours: 'catalogue.hoursInvalid',
  durationMinutes: 'catalogue.durationInvalid',
};
