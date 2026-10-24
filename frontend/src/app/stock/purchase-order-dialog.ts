import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';

import { applyProblem, fieldMessage, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { StockApi } from './stock-api.service';
import {
  fillPurchaseOrderForm,
  lineTotalCents,
  purchaseLine,
  PurchaseLineForm,
  purchaseOrderForm,
  purchaseTotalCents,
  toPurchaseOrderRequest,
} from './stock-forms';
import { Product, PurchaseOrder } from './stock.models';

/**
 * Creates a purchase order, or edits a draft. Open with `open(order, products)`: `products` are the workshop's
 * products, inactive ones too (a draft may still hold them); only active ones can be newly chosen.
 */
@Component({
  selector: 'app-purchase-order-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="po-dialog-title">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="po-dialog-title">
              {{ (editing() ? 'stock.po.edit' : 'stock.po.new') | t }}
              @if (editing(); as e) {
                <span class="text-body-secondary fw-normal">· {{ 'stock.po.numberValue' | t: { number: e.number } }}</span>
              }
            </h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert class="mb-3">{{ error() }}</app-alert>
            }
            <div class="row g-3">
              <div class="col-12">
                <label class="form-label" for="po-supplier">{{ 'stock.po.supplier' | t }}</label>
                <input id="po-supplier" class="form-control" formControlName="supplier" maxlength="120" [class.is-invalid]="!!supplierMessage()" />
                @if (supplierMessage(); as m) {
                  <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                }
              </div>
              <div class="col-12">
                <label class="form-label" for="po-notes">{{ 'stock.po.notes' | t }}</label>
                <textarea id="po-notes" class="form-control" rows="2" formControlName="notes" maxlength="1000"></textarea>
              </div>
            </div>

            <h3 class="h6 mt-4 mb-2">{{ 'stock.po.lines' | t }}</h3>
            <div class="vstack gap-3" formArrayName="lines">
              @for (line of lines.controls; track line; let i = $index) {
                <fieldset class="row g-2 align-items-end border rounded p-2 mx-0" [formGroupName]="i">
                  <legend class="visually-hidden">{{ 'stock.po.product' | t }} {{ i + 1 }}</legend>
                  <div class="col-12 col-md-5">
                    <label class="form-label small" [for]="'po-product-' + i">{{ 'stock.po.product' | t }}</label>
                    <select class="form-select" [id]="'po-product-' + i" formControlName="productId" (change)="productChosen(line)" [class.is-invalid]="line.controls.productId.invalid && line.controls.productId.touched">
                      <option value="">{{ 'stock.po.chooseProduct' | t }}</option>
                      @for (p of choices(line.controls.productId.value); track p.id) {
                        <option [value]="p.id">{{ p.name }} ({{ p.sku }})</option>
                      }
                    </select>
                  </div>
                  <div class="col-4 col-md-2">
                    <label class="form-label small" [for]="'po-qty-' + i">{{ 'stock.po.quantity' | t }}</label>
                    <input class="form-control" type="number" min="0.001" step="1" [id]="'po-qty-' + i" formControlName="quantity" [class.is-invalid]="line.controls.quantity.invalid && line.controls.quantity.touched" />
                  </div>
                  <div class="col-4 col-md-2">
                    <label class="form-label small" [for]="'po-cost-' + i">{{ 'stock.po.unitCost' | t }}</label>
                    <input class="form-control" type="number" min="0" step="0.01" [id]="'po-cost-' + i" formControlName="unitCostEuros" [class.is-invalid]="line.controls.unitCostEuros.invalid && line.controls.unitCostEuros.touched" />
                  </div>
                  <div class="col-4 col-md-3 d-flex align-items-end justify-content-end gap-2">
                    <span class="small text-body-secondary text-end text-nowrap pb-2">{{ lineTotal(i) | money }}</span>
                    <button type="button" class="btn btn-outline-secondary btn-icon" [attr.aria-label]="'stock.po.removeLine' | t" [title]="'stock.po.removeLine' | t" [disabled]="lines.length === 1" (click)="removeLine(i)">
                      <app-icon name="trash" [size]="16" />
                    </button>
                  </div>
                </fieldset>
              }
            </div>
            <div class="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3">
              <button type="button" class="btn btn-outline-primary btn-sm" (click)="addLine()"><app-icon name="plus" [size]="16" />{{ 'stock.po.addLine' | t }}</button>
              <span class="fw-semibold">{{ 'stock.po.total' | t }}: {{ total() | money }}</span>
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
export class PurchaseOrderDialog {
  /** Emits the order as saved by the server. */
  readonly saved = output<PurchaseOrder>();
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly form = purchaseOrderForm();
  protected readonly lines = this.form.controls.lines;
  protected readonly editing = signal<PurchaseOrder | null>(null);
  protected readonly products = signal<Product[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly total = computed(() => purchaseTotalCents((this.value().lines ?? []).map((l) => ({ quantity: l.quantity ?? null, unitCostEuros: l.unitCostEuros ?? null }))));

  open(order: PurchaseOrder | null, products: Product[]): void {
    this.products.set(products);
    this.error.set(null);
    this.busy.set(false);
    this.fill(order);
    const d = this.dialog().nativeElement;
    if (typeof d.showModal === 'function') d.showModal();
    else d.setAttribute('open', '');
  }

  close(): void {
    const d = this.dialog().nativeElement;
    if (typeof d.close === 'function') d.close();
    else d.removeAttribute('open');
  }

  /** Active products, plus the one already chosen on this line (it may have been deactivated since). */
  protected choices(chosen: string): Product[] {
    return this.products().filter((p) => p.active || p.id === chosen);
  }

  /** Suggests the product's cost when a product is chosen. */
  protected productChosen(line: PurchaseLineForm): void {
    const p = this.products().find((x) => x.id === line.controls.productId.value);
    if (p?.costCents != null) line.controls.unitCostEuros.setValue(p.costCents / 100);
  }

  protected lineTotal(i: number): number {
    const l = this.value().lines?.[i];
    return lineTotalCents(l?.quantity ?? null, l?.unitCostEuros ?? null);
  }

  protected addLine(): void {
    this.lines.push(purchaseLine());
  }

  protected removeLine(i: number): void {
    if (this.lines.length > 1) this.lines.removeAt(i);
  }

  protected supplierMessage(): MessageKey | null {
    return fieldMessage(this.form.controls.supplier, { required: 'stock.required' });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const current = this.editing();
    const call = current
      ? this.api.updatePurchaseOrder(current.id, toPurchaseOrderRequest(this.form, current.version))
      : this.api.createPurchaseOrder(toPurchaseOrderRequest(this.form));
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (po) => {
        this.busy.set(false);
        this.saved.emit(po);
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        if (current && isStale(e)) {
          this.api.purchaseOrder(current.id).subscribe({ next: (po) => this.fill(po), error: () => {} });
          this.error.set(this.i18n.error(e));
          return;
        }
        const marked = applyProblem(this.form, e);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  private fill(order: PurchaseOrder | null): void {
    this.editing.set(order);
    fillPurchaseOrderForm(this.form, order);
  }
}
