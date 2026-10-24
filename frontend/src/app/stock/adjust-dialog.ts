import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';

import { applyProblem, fieldMessage, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { formatQuantity } from './qty.pipe';
import { StockApi } from './stock-api.service';
import { adjustForm, AdjustForm, onHandAfter, signedQuantity } from './stock-forms';
import { Product } from './stock.models';

type Field = keyof AdjustForm['controls'];

/** Adds or removes stock of one product with a reason (owner and front desk). Open with `open(product)`. */
@Component({
  selector: 'app-adjust-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="adjust-dialog-title">
      <div class="modal-dialog">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <div class="overflow-hidden">
              <h2 class="modal-title" id="adjust-dialog-title">{{ 'stock.adjustTitle' | t }}</h2>
              @if (product(); as p) {
                <p class="small text-body-secondary mb-0 text-truncate">{{ p.name }} · {{ p.sku }}</p>
              }
            </div>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert class="mb-3">{{ error() }}</app-alert>
            }
            <p class="form-text mt-0 mb-3">{{ 'stock.adjustHelp' | t }}</p>
            <div class="row g-3">
              <div class="col-12">
                <span class="form-label d-block" id="ad-direction-label">{{ 'stock.adjustDirection' | t }}</span>
                <div class="btn-group w-100" role="radiogroup" aria-labelledby="ad-direction-label">
                  <input type="radio" class="btn-check" id="ad-add" value="add" formControlName="direction" />
                  <label class="btn btn-outline-primary" for="ad-add"><app-icon name="plus" [size]="16" />{{ 'stock.adjustAdd' | t }}</label>
                  <input type="radio" class="btn-check" id="ad-remove" value="remove" formControlName="direction" />
                  <label class="btn btn-outline-primary" for="ad-remove">{{ 'stock.adjustRemove' | t }}</label>
                </div>
              </div>
              <div class="col-sm-5">
                <label class="form-label" for="ad-quantity">{{ 'stock.adjustQuantity' | t }}@if (product(); as p) { <span class="text-body-secondary">({{ p.unit }})</span>}</label>
                <input id="ad-quantity" class="form-control" type="number" min="0.001" step="1" formControlName="quantity" [class.is-invalid]="!!message('quantity')" />
                @if (message('quantity'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-7 d-flex flex-column justify-content-end small text-body-secondary">
                <span>{{ 'stock.adjustNow' | t: { quantity: now() } }}</span>
                @if (after(); as a) {
                  <span [class.text-danger-emphasis]="a.negative" class="fw-semibold">{{ 'stock.adjustAfter' | t: { quantity: a.text } }}</span>
                }
              </div>
              <div class="col-12">
                <label class="form-label" for="ad-reason">{{ 'stock.adjustReason' | t }}</label>
                <input id="ad-reason" class="form-control" formControlName="reason" maxlength="500" [placeholder]="'stock.adjustReasonHint' | t" [class.is-invalid]="!!message('reason')" />
                @if (message('reason'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
              @if (busy()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'stock.adjust' | t }}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class AdjustDialog {
  /** Emits the product with its new stock. */
  readonly saved = output<Product>();
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly form = adjustForm();
  protected readonly product = signal<Product | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly now = computed(() => this.unitQuantity(this.product()?.onHand ?? null));
  /** On hand after the change, flagged when it would go below zero (the server refuses that). */
  protected readonly after = computed(() => {
    const p = this.product();
    const v = this.value();
    const result = onHandAfter(p?.onHand ?? null, v.direction ?? 'add', v.quantity ?? null);
    return result == null || !v.quantity ? null : { text: this.unitQuantity(result), negative: result < 0 };
  });

  open(product: Product): void {
    this.product.set(product);
    this.error.set(null);
    this.busy.set(false);
    this.form.reset({ direction: 'add', quantity: null, reason: '' });
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
    const p = this.product();
    if (!p) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api.adjust(p.id, signedQuantity(v.direction, v.quantity), v.reason.trim()).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.saved.emit(saved);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        const marked = applyProblem(this.form, e, { 'adjustment-reason-required': 'reason' }, { change: 'quantity' });
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected message(field: Field): MessageKey | null {
    return fieldMessage(this.form.controls[field], {
      required: field === 'quantity' ? 'stock.adjustQuantityInvalid' : 'stock.required',
      min: 'stock.adjustQuantityInvalid',
      decimals: 'stock.adjustQuantityInvalid',
    });
  }

  private unitQuantity(value: number | null): string {
    const p = this.product();
    return `${formatQuantity(value, this.i18n.lang())} ${p?.unit ?? ''}`.trim();
  }
}
