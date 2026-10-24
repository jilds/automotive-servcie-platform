import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { QtyPipe } from './qty.pipe';
import { StockApi } from './stock-api.service';
import { Movement, Product } from './stock.models';

/** The ledger of one product, newest first (owner and front desk). Open with `open(product)`. */
@Component({
  selector: 'app-movements-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, Alert, QtyPipe],
  template: `
    <dialog #dialog class="modal" aria-labelledby="movements-dialog-title">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <div class="overflow-hidden">
              <h2 class="modal-title" id="movements-dialog-title">{{ 'stock.movementsTitle' | t }}</h2>
              @if (product(); as p) {
                <p class="small text-body-secondary mb-0 text-truncate">{{ p.name }} · {{ p.sku }}</p>
              }
            </div>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }
            @if (movements(); as list) {
              @if (list.length) {
                <div class="table-stack">
                  <table class="table align-middle mb-2">
                    <thead>
                      <tr>
                        <th>{{ 'stock.movementDate' | t }}</th>
                        <th>{{ 'stock.movementType' | t }}</th>
                        <th class="text-end">{{ 'stock.movementQuantity' | t }}</th>
                        <th>{{ 'stock.movementDetail' | t }}</th>
                        <th>{{ 'stock.movementBy' | t }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (m of list; track m.id) {
                        <tr>
                          <td class="cell-title text-nowrap">{{ m.changedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                          <td [attr.data-label]="'stock.movementType' | t">
                            <span class="badge" [class]="badge(m)">{{ typeKey(m) | t }}</span>
                          </td>
                          <td class="text-end fw-semibold" [attr.data-label]="'stock.movementQuantity' | t">{{ m.quantity | qty: lang() : true }}</td>
                          <td class="text-break" [attr.data-label]="'stock.movementDetail' | t">
                            @if (m.reason) {
                              {{ m.reason }}
                            } @else if (m.referenceType === 'WORK_ORDER' && m.referenceId) {
                              <a [routerLink]="['/work-orders', m.referenceId]" (click)="close()">{{ 'stock.ref.WORK_ORDER' | t }}</a>
                            } @else if (m.referenceType) {
                              {{ refKey(m) | t }}
                            }
                          </td>
                          <td [attr.data-label]="'stock.movementBy' | t">{{ m.changedByName ?? '—' }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                <p class="form-text mb-0">{{ 'stock.movementsLimit' | t }}</p>
              } @else {
                <p class="text-body-secondary mb-0">{{ 'stock.movementsEmpty' | t }}</p>
              }
            } @else if (!error()) {
              <p class="text-body-secondary mb-0">{{ 'common.loading' | t }}</p>
            }
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.close' | t }}</button>
          </div>
        </div>
      </div>
    </dialog>
  `,
})
export class MovementsDialog {
  private readonly api = inject(StockApi);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly lang = this.i18n.lang;
  protected readonly product = signal<Product | null>(null);
  protected readonly movements = signal<Movement[] | null>(null);
  protected readonly error = signal<string | null>(null);

  open(product: Product): void {
    this.product.set(product);
    this.movements.set(null);
    this.error.set(null);
    this.api.movements(product.id).subscribe({
      next: (list) => this.movements.set(list),
      error: (e) => this.error.set(this.i18n.error(e)),
    });
    const d = this.dialog().nativeElement;
    if (typeof d.showModal === 'function') d.showModal();
    else d.setAttribute('open', '');
  }

  close(): void {
    const d = this.dialog().nativeElement;
    if (typeof d.close === 'function') d.close();
    else d.removeAttribute('open');
  }

  protected typeKey(m: Movement): MessageKey {
    return `stock.movement.${m.type}` as MessageKey;
  }

  protected refKey(m: Movement): MessageKey {
    return `stock.ref.${m.referenceType}` as MessageKey;
  }

  protected badge(m: Movement): string {
    switch (m.type) {
      case 'RECEIPT':
        return 'badge bg-success-subtle text-success-emphasis';
      case 'ISSUE':
        return 'badge bg-info-subtle text-info-emphasis';
      default:
        return 'badge bg-warning-subtle text-warning-emphasis';
    }
  }
}
