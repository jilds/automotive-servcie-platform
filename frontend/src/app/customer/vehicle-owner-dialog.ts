import { ChangeDetectionStrategy, Component, ElementRef, inject, OnDestroy, output, signal, viewChild } from '@angular/core';

import { ApiService } from '../core/api/api.service';
import { Customer, Vehicle } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { Plate } from '../shared/plate';

export interface OwnerChange {
  vehicle: Vehicle;
  from: Customer | null;
  to: Customer;
}

/**
 * Moves a vehicle, with its service history, to another customer (PUT /vehicles/{id}/owner).
 * Two steps: search and pick the new owner, then confirm "from → to". Open it with `open(vehicle, currentOwner?)`.
 */
@Component({
  selector: 'app-vehicle-owner-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, Icon, Plate],
  template: `
    <dialog #dialog class="modal" aria-labelledby="vehicle-owner-title">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title d-flex flex-wrap align-items-center gap-2" id="vehicle-owner-title">
              {{ 'vehicles.changeOwner' | t }}
              @if (vehicle(); as v) {
                <app-plate [plate]="v.plateDisplay ?? v.plate" [era]="v.plateEra" [country]="v.registrationCountry" [registrationDate]="v.registrationDate" size="sm" />
              }
            </h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            @if (error()) {
              <app-alert>{{ error() }}</app-alert>
            }
            @if (target(); as to) {
              <p>{{ 'vehicles.ownerConfirm' | t }}</p>
              <div class="d-flex flex-wrap align-items-center gap-3">
                <dl class="mb-0">
                  <dt class="small text-body-secondary fw-semibold">{{ 'vehicles.ownerFrom' | t }}</dt>
                  <dd class="mb-0">{{ from()?.name ?? '—' }}</dd>
                </dl>
                <app-icon name="arrow-right" />
                <dl class="mb-0">
                  <dt class="small text-body-secondary fw-semibold">{{ 'vehicles.ownerTo' | t }}</dt>
                  <dd class="mb-0"><strong>{{ to.name }}</strong></dd>
                </dl>
              </div>
            } @else {
              <div class="input-group">
                <span class="input-group-text"><app-icon name="search" [size]="18" /></span>
                <input #query type="search" class="form-control" [attr.aria-label]="'vehicles.ownerSearch' | t" [placeholder]="'vehicles.ownerSearch' | t" (input)="search(query.value)" />
              </div>
              <div class="list-group mt-3" [attr.aria-busy]="searching()">
                @for (c of results(); track c.id) {
                  <button type="button" class="list-group-item list-group-item-action" (click)="pick(c)">
                    <strong>{{ c.name }}</strong> <span class="text-body-secondary small">{{ c.nif }} {{ c.phone }}</span>
                  </button>
                } @empty {
                  @if (!searching()) {
                    <p class="text-body-secondary mb-0">{{ 'vehicles.ownerNoResults' | t }}</p>
                  }
                }
              </div>
            }
          </div>
          <div class="modal-footer">
            @if (target()) {
              <button type="button" class="btn btn-outline-secondary" (click)="back()"><app-icon name="chevron-left" [size]="16" />{{ 'common.back' | t }}</button>
              <button type="button" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="confirm()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                }
                {{ 'vehicles.ownerSubmit' | t }}
              </button>
            } @else {
              <button type="button" class="btn btn-outline-secondary" (click)="close()">{{ 'common.cancel' | t }}</button>
            }
          </div>
        </div>
      </div>
    </dialog>
  `,
})
export class VehicleOwnerDialog implements OnDestroy {
  /** Emits after the server moved the vehicle. */
  readonly transferred = output<OwnerChange>();
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly from = signal<Customer | null>(null);
  protected readonly target = signal<Customer | null>(null);
  protected readonly results = signal<Customer[]>([]);
  protected readonly searching = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  /** Opens on the search step. Without `currentOwner` the current owner is loaded for the confirm step. */
  open(vehicle: Vehicle, currentOwner?: Customer | null): void {
    this.vehicle.set(vehicle);
    this.target.set(null);
    this.error.set(null);
    this.busy.set(false);
    this.from.set(currentOwner ?? null);
    if (!currentOwner) this.api.customer(vehicle.customerId).subscribe({ next: (c) => this.from.set(c), error: () => {} });
    this.runSearch('');
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    clearTimeout(this.timer);
    this.dialog().nativeElement.close();
  }

  search(q: string): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.runSearch(q), 250);
  }

  pick(customer: Customer): void {
    this.error.set(null);
    this.target.set(customer);
  }

  back(): void {
    this.error.set(null);
    this.target.set(null);
  }

  confirm(): void {
    const vehicle = this.vehicle();
    const to = this.target();
    if (!vehicle || !to) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.transferVehicle(vehicle.id, to.id).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.transferred.emit({ vehicle: updated, from: this.from(), to });
        this.close();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  private runSearch(q: string): void {
    const ownerId = this.vehicle()?.customerId;
    this.searching.set(true);
    this.api.customers(q.trim() || undefined).subscribe({
      next: (list) => {
        this.results.set(list.filter((c) => c.id !== ownerId));
        this.searching.set(false);
      },
      error: (e) => {
        this.searching.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }
}
