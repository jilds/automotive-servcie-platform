import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';

import { applyProblem, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { CatalogueApi } from './catalogue-api.service';
import { formatQuantity } from '../stock/qty.pipe';
import { CatalogueService, CataloguePackage, PackageProductChoice } from './catalogue.models';
import { fillPackageForm, itemRef, listPriceCents, PACKAGE_FIELDS, packageForm, packageItem, parseItemRef, toPackageRequest } from './catalogue-forms';

/**
 * Creates or edits a package of services and stock products sold for one price. Open with
 * `open(pkg, services, products)`, or `open(pkg, services, products, true)` for a copy saved as a new package;
 * `services` and `products` are what each row can choose from, in two groups of one select (inactive ones only appear
 * when the package already holds them). A chosen product shows what is available in stock.
 */
@Component({
  selector: 'app-package-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, MoneyPipe, Alert, Icon],
  template: `
    <dialog #dialog class="modal" aria-labelledby="package-dialog-title">
      <div class="modal-dialog modal-lg">
        <form class="modal-content" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="modal-header">
            <h2 class="modal-title" id="package-dialog-title">{{ (editing() ? 'catalogue.editPackage' : copying() ? 'catalogue.copyPackage' : 'catalogue.newPackage') | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert class="mb-3">{{ error() }}</app-alert>
            }
            <div class="row g-3">
              <div class="col-12">
                <label class="form-label" for="pd-name">{{ 'catalogue.name' | t }}</label>
                <input id="pd-name" class="form-control" formControlName="name" maxlength="120" [class.is-invalid]="invalid('name')" />
                @if (invalid('name')) {
                  <div class="invalid-feedback">{{ 'catalogue.required' | t }}</div>
                }
              </div>
              <div class="col-12">
                <label class="form-label" for="pd-description">{{ 'catalogue.description' | t }}</label>
                <textarea id="pd-description" class="form-control" rows="2" formControlName="description" maxlength="1000"></textarea>
              </div>

              <fieldset class="col-12" formArrayName="items">
                <legend class="form-label fs-6 mb-2">{{ 'catalogue.items' | t }}</legend>
                <div class="vstack gap-2">
                  @for (item of form.controls.items.controls; track item; let i = $index) {
                    <div class="d-flex gap-2 align-items-start" [formGroupName]="i">
                      <div class="flex-grow-1" style="min-width: 0">
                        <select class="form-select" formControlName="ref" [attr.aria-label]="'catalogue.itemChoice' | t" [class.is-invalid]="item.controls.ref.invalid && item.controls.ref.touched">
                          <option value="">—</option>
                          <optgroup [label]="'catalogue.services' | t">
                            @for (s of serviceChoices(); track s.id) {
                              <option [value]="'s:' + s.id">{{ s.name }}{{ s.active ? '' : ' (' + ('catalogue.inactiveItem' | t) + ')' }}</option>
                            }
                          </optgroup>
                          @if (productChoices().length) {
                            <optgroup [label]="'catalogue.productsGroup' | t">
                              @for (p of productChoices(); track p.id) {
                                <option [value]="'p:' + p.id">{{ p.name }} ({{ p.sku }}){{ p.active ? '' : ' (' + ('catalogue.inactiveProduct' | t) + ')' }}</option>
                              }
                            </optgroup>
                          }
                        </select>
                        @if (availability(item.controls.ref.value); as stock) {
                          <div class="form-text" [class.text-danger]="stock.short">{{ 'catalogue.availableStock' | t: { qty: stock.qty, unit: stock.unit } }}</div>
                        }
                      </div>
                      <input class="form-control flex-shrink-0" style="width: 5rem" type="number" min="1" max="99" formControlName="quantity" [attr.aria-label]="'catalogue.quantity' | t" />
                      <button type="button" class="btn btn-outline-secondary btn-icon flex-shrink-0" [attr.aria-label]="'catalogue.removeItem' | t" [title]="'catalogue.removeItem' | t" [disabled]="form.controls.items.length === 1" (click)="removeItem(i)">
                        <app-icon name="trash" [size]="16" />
                      </button>
                    </div>
                  }
                </div>
                <button type="button" class="btn btn-sm btn-outline-secondary mt-2" (click)="addItem()"><app-icon name="plus" [size]="16" />{{ 'catalogue.addItem' | t }}</button>
                @if (itemsError()) {
                  <div class="invalid-feedback d-block">{{ 'catalogue.itemsRequired' | t }}</div>
                }
              </fieldset>

              <div class="col-sm-6">
                <label class="form-label" for="pd-price">{{ 'catalogue.price' | t }}</label>
                <input id="pd-price" class="form-control" type="number" min="0" step="0.01" formControlName="priceEuros" [class.is-invalid]="invalid('priceEuros')" />
                @if (invalid('priceEuros')) {
                  <div class="invalid-feedback">{{ 'catalogue.amountInvalid' | t }}</div>
                }
                <div class="form-text">
                  {{ 'catalogue.listPrice' | t: { price: (listCents() | money) } }}
                  @if (savingCents() > 0) {
                    · {{ 'catalogue.saving' | t: { amount: (savingCents() | money) } }}
                  }
                </div>
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="pd-vat">{{ 'catalogue.vat' | t }}</label>
                <input id="pd-vat" class="form-control" type="number" min="0" max="100" step="0.01" formControlName="vatRate" [class.is-invalid]="invalid('vatRate')" />
                @if (invalid('vatRate')) {
                  <div class="invalid-feedback">{{ 'catalogue.vatInvalid' | t }}</div>
                }
              </div>
              <div class="col-12">
                <div class="form-check">
                  <input id="pd-active" class="form-check-input" type="checkbox" formControlName="active" />
                  <label class="form-check-label" for="pd-active">{{ 'catalogue.activeLabel' | t }}</label>
                </div>
                <div class="form-check mt-2">
                  <input id="pd-online" class="form-check-input" type="checkbox" formControlName="listedOnline" />
                  <label class="form-check-label" for="pd-online">{{ 'catalogue.listedOnline' | t }}</label>
                  <div class="form-text">{{ 'catalogue.listedOnlineHelp' | t }}</div>
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
export class PackageDialog {
  /** Emits the package as saved by the server. */
  readonly saved = output<CataloguePackage>();
  private readonly api = inject(CatalogueApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly form = packageForm();
  protected readonly editing = signal<CataloguePackage | null>(null);
  /** True while the dialog holds a copy of a package, to be saved as a new one. */
  protected readonly copying = signal(false);
  private readonly services = signal<CatalogueService[]>([]);
  private readonly products = signal<PackageProductChoice[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly items = toSignal(this.form.controls.items.valueChanges.pipe(map(() => this.form.controls.items.getRawValue())), {
    initialValue: this.form.controls.items.getRawValue(),
  });
  private readonly price = toSignal(this.form.controls.priceEuros.valueChanges, { initialValue: this.form.controls.priceEuros.value });

  /** What the package being edited holds, as row choices (`s:<id>`, `p:<id>`). */
  private readonly held = computed(() => new Set(this.editing()?.items.map(itemRef) ?? []));
  /** Offered services, plus inactive ones the package already holds. */
  protected readonly serviceChoices = computed(() => this.services().filter((s) => s.active || this.held().has('s:' + s.id)));
  /** Products still sold, plus ones no longer sold that the package already holds. */
  protected readonly productChoices = computed(() => this.products().filter((p) => p.active || this.held().has('p:' + p.id)));
  protected readonly listCents = computed(() => listPriceCents(this.items(), this.services(), this.products()));
  protected readonly savingCents = computed(() => this.listCents() - Math.round(Number(this.price() ?? 0) * 100));

  /**
   * @param products the workshop's stock products (empty when stock could not be read: rows then offer services only)
   * @param copy     fill the dialog with a copy of `pkg` (without items no longer offered), saved as a new package
   */
  open(pkg: CataloguePackage | null, services: CatalogueService[], products: PackageProductChoice[] = [], copy = false): void {
    this.error.set(null);
    this.busy.set(false);
    this.services.set(services);
    this.products.set(products);
    if (pkg && copy) {
      this.editing.set(null);
      this.copying.set(true);
      fillPackageForm(this.form, pkg, this.i18n.t('catalogue.copySuffix'));
    } else {
      this.fill(pkg);
    }
    const d = this.dialog().nativeElement;
    if (typeof d.showModal === 'function') d.showModal();
    else d.setAttribute('open', '');
  }

  close(): void {
    const d = this.dialog().nativeElement;
    if (typeof d.close === 'function') d.close();
    else d.removeAttribute('open');
  }

  addItem(): void {
    this.form.controls.items.push(packageItem());
  }

  removeItem(index: number): void {
    if (this.form.controls.items.length > 1) this.form.controls.items.removeAt(index);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const current = this.editing();
    const call = current
      ? this.api.updatePackage(current.id, toPackageRequest(this.form, current.version))
      : this.api.createPackage(toPackageRequest(this.form));
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
          this.api.servicePackage(current.id).subscribe({ next: (p) => this.fill(p), error: () => {} });
          this.error.set(this.i18n.error(e));
          return;
        }
        const marked = applyProblem(this.form, e, { 'package-empty': 'items' }, PACKAGE_FIELDS);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
      },
    });
  }

  protected invalid(field: 'name' | 'priceEuros' | 'vatRate'): boolean {
    const c = this.form.controls[field];
    return c.invalid && (c.touched || c.dirty);
  }

  protected itemsError(): boolean {
    const items = this.form.controls.items;
    return items.touched && items.controls.every((i) => !i.controls.ref.value);
  }

  /** For a row holding a product: what is available in stock, in the UI language, and whether the row asks for more. */
  protected availability(ref: string): { qty: string; unit: string; short: boolean } | null {
    const chosen = parseItemRef(ref);
    if (chosen?.kind !== 'PRODUCT') return null;
    const product = this.products().find((p) => p.id === chosen.id);
    if (!product) return null;
    const row = this.items().find((i) => i.ref === ref);
    return {
      qty: formatQuantity(product.available, this.i18n.lang()),
      unit: product.unit,
      short: product.available < Number(row?.quantity ?? 1),
    };
  }

  private fill(pkg: CataloguePackage | null): void {
    this.editing.set(pkg);
    this.copying.set(false);
    fillPackageForm(this.form, pkg);
  }
}
