import { ChangeDetectionStrategy, Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { applyProblem, fieldMessage, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { StockApi } from './stock-api.service';
import { PRODUCT_FIELDS, productForm, ProductForm, productFormValue, toProductRequest } from './stock-forms';
import { Product } from './stock.models';

type Field = keyof ProductForm['controls'];

/** Creates or edits a product (owner only). Open with `open(product)` to edit or `open(null)` for a new one. */
@Component({
  selector: 'app-product-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="product-dialog-title">
      <div class="modal-dialog modal-lg">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="product-dialog-title">{{ (editing() ? 'stock.editProduct' : 'stock.newProduct') | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert class="mb-3">{{ error() }}</app-alert>
            }
            @if (!editing()) {
              <p class="form-text mt-0 mb-3">{{ 'stock.newProductHelp' | t }}</p>
            }
            <div class="row g-3">
              <div class="col-sm-4">
                <label class="form-label" for="pd-sku">{{ 'stock.sku' | t }}</label>
                <input id="pd-sku" class="form-control" formControlName="sku" maxlength="40" autocomplete="off" [class.is-invalid]="!!message('sku')" />
                @if (message('sku'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-8">
                <label class="form-label" for="pd-name">{{ 'stock.name' | t }}</label>
                <input id="pd-name" class="form-control" formControlName="name" maxlength="120" [class.is-invalid]="!!message('name')" />
                @if (message('name'); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-sm-4">
                <label class="form-label" for="pd-brand">{{ 'stock.brand' | t }}</label>
                <input id="pd-brand" class="form-control" formControlName="brand" maxlength="60" />
              </div>
              <div class="col-6 col-sm-4">
                <label class="form-label" for="pd-unit">{{ 'stock.unit' | t }}</label>
                <input id="pd-unit" class="form-control" formControlName="unit" maxlength="10" [placeholder]="'stock.unitHint' | t" />
              </div>
              <div class="col-6 col-sm-4">
                <label class="form-label" for="pd-bin">{{ 'stock.bin' | t }}</label>
                <input id="pd-bin" class="form-control" formControlName="bin" maxlength="40" [placeholder]="'stock.binHint' | t" />
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="pd-cost">{{ 'stock.cost' | t }}</label>
                <input id="pd-cost" class="form-control" type="number" min="0" step="0.01" formControlName="costEuros" [class.is-invalid]="!!message('costEuros')" />
                @if (message('costEuros'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="pd-price">{{ 'stock.salePrice' | t }}</label>
                <input id="pd-price" class="form-control" type="number" min="0" step="0.01" formControlName="salePriceEuros" [class.is-invalid]="!!message('salePriceEuros')" />
                @if (message('salePriceEuros'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="pd-vat">{{ 'stock.vat' | t }}</label>
                <input id="pd-vat" class="form-control" type="number" min="0" max="100" step="0.01" formControlName="vatRate" [class.is-invalid]="!!message('vatRate')" />
                @if (message('vatRate'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-sm-6 col-lg-3">
                <label class="form-label" for="pd-min">{{ 'stock.minStock' | t }}</label>
                <input id="pd-min" class="form-control" type="number" min="0" step="1" formControlName="minStock" [class.is-invalid]="!!message('minStock')" aria-describedby="pd-min-help" />
                @if (message('minStock'); as m) {
                  <div class="invalid-feedback">{{ m | t }}</div>
                }
              </div>
              <div class="col-12 form-text mt-1" id="pd-min-help">{{ 'stock.minStockHelp' | t }}</div>
              <div class="col-12">
                <div class="form-check">
                  <input id="pd-active" class="form-check-input" type="checkbox" formControlName="active" />
                  <label class="form-check-label" for="pd-active">{{ 'stock.activeLabel' | t }}</label>
                </div>
                <div class="form-check mt-2">
                  <input id="pd-online" class="form-check-input" type="checkbox" formControlName="listedOnline" aria-describedby="pd-online-help" />
                  <label class="form-check-label" for="pd-online">{{ 'stock.listedOnline' | t }}</label>
                  <div class="form-text" id="pd-online-help">{{ 'stock.listedOnlineHelp' | t }}</div>
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
export class ProductDialog {
  /** Emits the product as saved by the server. */
  readonly saved = output<Product>();
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly form = productForm();
  protected readonly editing = signal<Product | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  open(product: Product | null): void {
    this.error.set(null);
    this.busy.set(false);
    this.fill(product);
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
      ? this.api.updateProduct(current.id, toProductRequest(this.form, current.version))
      : this.api.createProduct(toProductRequest(this.form));
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (p) => {
        this.busy.set(false);
        this.saved.emit(p);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        if (current && isStale(e)) {
          // Saved elsewhere since this dialog opened: show the stored product, then say why.
          this.api.product(current.id).subscribe({ next: (p) => this.fill(p), error: () => {} });
          this.error.set(this.i18n.error(e));
          return;
        }
        const marked = applyProblem(this.form, e, { 'duplicate-sku': 'sku' }, PRODUCT_FIELDS);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected message(field: Field): MessageKey | null {
    return fieldMessage(this.form.controls[field], {
      required: MESSAGES[field] ?? 'stock.required',
      min: MESSAGES[field],
      max: MESSAGES[field],
      decimals: MESSAGES[field],
    });
  }

  private fill(product: Product | null): void {
    this.editing.set(product);
    this.form.reset(productFormValue(product));
  }
}

const MESSAGES: Partial<Record<Field, MessageKey>> = {
  costEuros: 'stock.amountInvalid',
  salePriceEuros: 'stock.amountInvalid',
  vatRate: 'stock.vatInvalid',
  minStock: 'stock.quantityInvalid',
};
