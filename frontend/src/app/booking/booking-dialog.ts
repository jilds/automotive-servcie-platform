import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { Customer, Vehicle } from '../core/api/models';
import { fieldMessage, isStale, problemCode, problemOf, SERVER_ERROR } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { CustomerEditDialog } from '../customer/customer-edit-dialog';
import { VehicleEditDialog } from '../customer/vehicle-edit-dialog';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { Plate } from '../shared/plate';
import { formatPlate, ptPlateEra } from '../shared/plate-format';
import { WorkshopCountry } from '../shared/workshop-country';
import { BookingApi } from './booking-api.service';
import { durationKey, formatInstant, LONG_DATE, STATUS_BADGE, statusText, TIME } from './booking-calendar';
import { Booking, SlotDay } from './booking.models';
import { PickedSlot, SlotPicker } from './slot-picker';
import { WorkshopZone } from './workshop-zone';

/** What the dialog shows below the details: the actions, the decline reason, or the slot picker to move. */
type Mode = 'view' | 'decline' | 'move';

/** A change the page reports and reloads its lists for. */
export interface BookingChange {
  booking: Booking;
  message: string;
}

/** Actions the front desk may take now, from the status (moves in `docs/api/release-3.md`). */
export function bookingActions(b: Booking, now = new Date()) {
  const past = new Date(b.slotStart).getTime() <= now.getTime();
  return {
    confirm: b.status === 'REQUESTED',
    decline: b.status === 'REQUESTED',
    move: b.status === 'REQUESTED' || b.status === 'CONFIRMED',
    noShow: b.status === 'CONFIRMED' && past,
    checkIn: b.status === 'CONFIRMED',
  };
}

/**
 * Detail of a booking for the owner and the front desk (`/bookings`): contact, plate, services and note, and the
 * actions confirm / decline (with a reason) / move (to any free slot) / no-show / check-in. A check-in for a plate
 * the workshop does not know yet first opens the customer dialog, then the vehicle dialog, prefilled from the booking.
 */
@Component({
  selector: 'app-booking-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, Plate, SlotPicker, CustomerEditDialog, VehicleEditDialog],
  template: `
    <dialog #dialog class="modal" aria-labelledby="bd-title" (close)="mode.set('view')">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title" id="bd-title">
              @if (booking(); as b) {
                {{ 'booking.detail.title' | t: { number: b.number } }}
              } @else {
                {{ 'common.loading' | t }}
              }
            </h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="close()"></button>
          </div>
          <div class="modal-body">
            <div aria-live="polite">
              @if (error()) {
                <app-alert>{{ error() }}</app-alert>
              }
            </div>
            @if (booking(); as b) {
              <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
                <span class="badge" [class]="badge()">{{ statusKey() | t }}</span>
                <span class="fw-semibold">{{ when(b.slotStart) }}</span>
              </div>
              <dl class="row g-3 mb-3">
                <div class="col-sm-6">
                  <dt class="small text-body-secondary fw-semibold">{{ 'booking.detail.contact' | t }}</dt>
                  <dd class="mb-0 text-break">
                    <span class="d-block fw-semibold">{{ b.name }}</span>
                    @if (b.phone) {
                      <a class="d-block" [href]="telHref(b.phone)">{{ b.phone }}</a>
                    }
                    @if (b.email) {
                      <a class="d-block" [href]="'mailto:' + encode(b.email)">{{ b.email }}</a>
                    }
                    @if (b.marketingConsent) {
                      <span class="d-block small text-body-secondary">{{ 'customers.marketingConsent' | t }}</span>
                    }
                  </dd>
                </div>
                <div class="col-sm-6">
                  <dt class="small text-body-secondary fw-semibold">{{ 'vehicles.plate' | t }}</dt>
                  <dd class="mb-0">
                    <app-plate [plate]="plate()" [era]="era()" [country]="era() ? 'PT' : null" size="sm" />
                    <span class="d-block small text-body-secondary mt-1">{{ (b.vehicleId ? 'booking.detail.knownVehicle' : 'booking.detail.newVehicle') | t }}</span>
                  </dd>
                </div>
                <div class="col-12">
                  <dt class="small text-body-secondary fw-semibold">{{ 'booking.detail.note' | t }}</dt>
                  <dd class="mb-0 text-break">{{ b.note || ('booking.detail.noNote' | t) }}</dd>
                </div>
                <div class="col-12">
                  <dt class="small text-body-secondary fw-semibold">{{ 'booking.detail.services' | t }}</dt>
                  <dd class="mb-0">
                    <ul class="list-unstyled vstack gap-1 mb-0">
                      @for (s of b.services; track s.serviceId) {
                        <li class="d-flex justify-content-between gap-3">
                          <span class="text-break">{{ s.name }} <span class="small text-body-secondary">· {{ duration(s.durationMinutes) }}</span></span>
                          <span class="text-nowrap">{{ s.priceCents | money }}</span>
                        </li>
                      }
                      <li class="d-flex justify-content-between gap-3 border-top pt-1 fw-semibold">
                        <span>{{ 'booking.detail.totalNet' | t }}</span>
                        <span class="text-nowrap">{{ netCents() | money }}</span>
                      </li>
                    </ul>
                  </dd>
                </div>
                @if (b.declineReason) {
                  <div class="col-12">
                    <dt class="small text-body-secondary fw-semibold">{{ 'booking.decline.reason' | t }}</dt>
                    <dd class="mb-0 text-break">{{ b.declineReason }}</dd>
                  </div>
                }
                <div class="col-12 small text-body-secondary">
                  {{ 'booking.detail.requestedAt' | t: { date: when(b.createdAt) } }}
                  @if (b.decidedAt) {
                    · {{ 'booking.detail.decidedBy' | t: { name: b.decidedByName ?? '—', date: when(b.decidedAt) } }}
                  }
                </div>
              </dl>

              @switch (mode()) {
                @case ('decline') {
                  <form class="border rounded-3 p-3" (submit)="$event.preventDefault(); decline(b)" novalidate>
                    <label class="form-label" for="bd-reason">{{ 'booking.decline.reason' | t }}</label>
                    <textarea id="bd-reason" class="form-control" rows="2" maxlength="500" [formControl]="reason" [class.is-invalid]="!!reasonMessage()" [attr.aria-invalid]="!!reasonMessage()" aria-describedby="bd-reason-help"></textarea>
                    @if (reasonMessage(); as m) {
                      <div class="invalid-feedback" id="bd-reason-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                    } @else {
                      <div class="form-text" id="bd-reason-help">{{ 'booking.decline.reasonHint' | t }}</div>
                    }
                    <div class="d-flex flex-wrap justify-content-end gap-2 mt-3">
                      <button type="button" class="btn btn-outline-secondary" (click)="mode.set('view')">{{ 'common.cancel' | t }}</button>
                      <button type="submit" class="btn btn-danger" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'booking.decline.submit' | t }}</button>
                    </div>
                  </form>
                }
                @case ('move') {
                  <section class="border rounded-3 p-3" aria-labelledby="bd-move-title">
                    <h3 class="h6 mb-1" id="bd-move-title">{{ 'booking.move.title' | t }}</h3>
                    <p class="small text-body-secondary">{{ 'booking.move.hint' | t }}</p>
                    <app-slot-picker [source]="slotSource" [timeZone]="zone()" [value]="target()?.start ?? null" [startDay]="target()?.date ?? null" (picked)="target.set($event)" />
                    <div class="d-flex flex-wrap justify-content-end gap-2 mt-3">
                      <button type="button" class="btn btn-outline-secondary" (click)="mode.set('view')">{{ 'common.cancel' | t }}</button>
                      <button type="button" class="btn btn-primary" [disabled]="!target() || busy()" [attr.aria-busy]="busy()" (click)="move(b)">
                        {{ 'booking.move.submit' | t }}
                      </button>
                    </div>
                  </section>
                }
              }
            } @else if (!error()) {
              <div class="placeholder-glow vstack gap-2" aria-hidden="true">
                <span class="placeholder placeholder-lg col-6"></span>
                <span class="placeholder col-10"></span>
                <span class="placeholder col-8"></span>
              </div>
            }
          </div>
          @if (booking(); as b) {
            @if (mode() === 'view') {
              <div class="modal-footer flex-wrap gap-2">
                @if (b.workOrderId) {
                  <a class="btn btn-outline-secondary" [routerLink]="['/work-orders', b.workOrderId]" (click)="close()">{{ 'booking.action.openWorkOrder' | t }}</a>
                }
                @if (actions().decline) {
                  <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="startDecline()">{{ 'booking.action.decline' | t }}</button>
                }
                @if (actions().noShow) {
                  <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="noShow(b)">{{ 'booking.action.noShow' | t }}</button>
                }
                @if (actions().move) {
                  <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="startMove()">{{ 'booking.action.move' | t }}</button>
                }
                @if (actions().confirm) {
                  <button type="button" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()" (click)="confirm(b)"><app-icon name="check-circle" [size]="16" />{{ 'booking.action.confirm' | t }}</button>
                }
                @if (actions().checkIn) {
                  <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="checkIn(b)"><app-icon name="check-in" [size]="16" />{{ 'booking.action.checkIn' | t }}</button>
                }
              </div>
            }
          }
        </div>
      </div>
    </dialog>

    <app-customer-edit-dialog #customerDialog (saved)="customerCreated($event)" />
    <app-vehicle-edit-dialog #vehicleDialog (saved)="vehicleCreated($event)" />
  `,
})
export class BookingDialog {
  private readonly api = inject(BookingApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly workshopCountry = inject(WorkshopCountry);
  /** The workshop's time zone (loaded by the bookings page): times and the move picker follow it. */
  protected readonly zone = inject(WorkshopZone).zone;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly customerDialog = viewChild.required<CustomerEditDialog>('customerDialog');
  private readonly vehicleDialog = viewChild.required<VehicleEditDialog>('vehicleDialog');

  /** The booking changed (confirmed, declined, moved, missed): the page reloads its lists and shows the message. */
  readonly changed = output<BookingChange>();

  protected readonly booking = signal<Booking | null>(null);
  protected readonly mode = signal<Mode>('view');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly target = signal<PickedSlot | null>(null);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] });
  private readonly reasonTick = signal(0);

  protected readonly actions = computed(() => {
    const b = this.booking();
    return b ? bookingActions(b) : bookingActions({ status: 'CANCELLED', slotStart: new Date().toISOString() } as Booking);
  });
  protected readonly badge = computed(() => STATUS_BADGE[this.booking()?.status ?? 'REQUESTED']);
  protected readonly statusKey = computed<MessageKey>(() => statusText(this.booking()?.status ?? 'REQUESTED'));
  protected readonly plate = computed(() => formatPlate(this.booking()?.plate ?? '', 'PT'));
  protected readonly era = computed(() => ptPlateEra(this.booking()?.plate ?? ''));
  protected readonly netCents = computed(() => (this.booking()?.services ?? []).reduce((sum, s) => sum + s.priceCents, 0));

  /** Free slots for a move: the staff endpoint offers every free slot (no public lead time or horizon). */
  protected readonly slotSource = (from: string, days: number): Observable<SlotDay[]> => this.api.staffSlots(from, days);

  constructor() {
    this.reason.events.pipe(takeUntilDestroyed()).subscribe(() => this.reasonTick.update((n) => n + 1));
  }

  open(id: string): void {
    this.booking.set(null);
    this.mode.set('view');
    this.error.set(null);
    this.busy.set(false);
    this.dialog().nativeElement.showModal();
    this.load(id);
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected when(instant: string): string {
    const lang = this.i18n.lang();
    return `${formatInstant(instant, lang, LONG_DATE, this.zone())}, ${formatInstant(instant, lang, TIME, this.zone())}`;
  }

  protected duration(minutes: number): string {
    const d = durationKey(minutes);
    return this.i18n.t(d.key, d.params);
  }

  /** A dialable number only: digits and a leading +, nothing a link could misuse. */
  protected telHref(phone: string): string {
    return 'tel:' + phone.replace(/[^\d+]/g, '');
  }

  protected encode(value: string): string {
    return encodeURIComponent(value);
  }

  protected reasonMessage(): MessageKey | null {
    this.reasonTick();
    return fieldMessage(this.reason, { required: 'booking.decline.reasonRequired', maxlength: 'booking.contact.tooLong' });
  }

  protected startDecline(): void {
    this.reason.reset('');
    this.mode.set('decline');
    setTimeout(() => document.getElementById('bd-reason')?.focus());
  }

  protected startMove(): void {
    this.target.set(null);
    this.mode.set('move');
  }

  confirm(b: Booking): void {
    this.run(this.api.confirm(b.id, b.version), 'booking.done.confirmed');
  }

  decline(b: Booking): void {
    this.reason.markAsTouched();
    const reason = this.reason.value.trim();
    if (!reason) {
      this.reason.setValue('');
      this.reason.markAsTouched();
      this.reasonTick.update((n) => n + 1);
      return;
    }
    this.run(this.api.decline(b.id, reason, b.version), 'booking.done.declined');
  }

  move(b: Booking): void {
    const target = this.target();
    if (target) this.run(this.api.move(b.id, target.start, b.version), 'booking.done.moved');
  }

  noShow(b: Booking): void {
    this.run(this.api.noShow(b.id, b.version), 'booking.done.noShow');
  }

  /**
   * To the check-in screen with the booking. A plate the workshop does not know yet: register the customer, then
   * the vehicle (both dialogs prefilled from the booking), then check in.
   */
  checkIn(b: Booking): void {
    if (b.vehicleId) {
      this.close();
      this.router.navigate(['/check-in'], { queryParams: { booking: b.id } });
      return;
    }
    this.close();
    this.customerDialog().openNew({ name: b.name, phone: b.phone, email: b.email, marketingConsent: b.marketingConsent });
  }

  protected customerCreated(customer: Customer): void {
    const b = this.booking();
    if (!b) return;
    const plate = formatPlate(b.plate, 'PT');
    this.vehicleDialog().openNew(customer.id, plate, this.workshopCountry.country());
  }

  protected vehicleCreated(_vehicle: Vehicle): void {
    const b = this.booking();
    if (b) this.router.navigate(['/check-in'], { queryParams: { booking: b.id } });
  }

  private run(request: Observable<Booking>, done: MessageKey): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (b) => {
        this.busy.set(false);
        this.booking.set(b);
        this.mode.set('view');
        this.changed.emit({ booking: b, message: this.i18n.t(done, { number: b.number, date: this.when(b.slotStart) }) });
        this.close();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        if (isStale(e)) {
          // Changed by someone else: show the stored booking and say why.
          this.error.set(this.i18n.error(e));
          this.mode.set('view');
          const id = this.booking()?.id;
          if (id) this.load(id, false);
          return;
        }
        if (problemCode(e) === 'decline-reason-required' || (problemCode(e) === 'validation' && problemOf(e)?.errors?.['reason'])) {
          // Shown under the reason field (error.decline-reason-required).
          this.reason.setErrors({ [SERVER_ERROR]: 'decline-reason-required' });
          this.reason.markAsTouched();
          this.reasonTick.update((n) => n + 1);
          return;
        }
        this.error.set(this.i18n.error(e));
      },
    });
  }

  private load(id: string, clearError = true): void {
    if (clearError) this.error.set(null);
    this.api.booking(id).subscribe({
      next: (b) => this.booking.set(b),
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }
}
