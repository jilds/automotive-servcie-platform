import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { applyProblem, fieldMessage, problemCode, problemOf } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { MoneyPipe } from '../shared/money.pipe';
import { PublicPage } from '../shared/public-page';
import { BookingApi } from './booking-api.service';
import { durationKey, formatDay } from './booking-calendar';
import { contactForm, NOTE_MAX, toBookingRequest } from './booking-forms';
import { BookingCreated, PublicService } from './booking.models';
import { PickedSlot, SlotPicker } from './slot-picker';

export type BookingStep = 'services' | 'slot' | 'contact' | 'done';
/** Why the page cannot take a booking: unknown or closed workshop, booking add-on off, too many requests. */
export type PageState = 'ok' | 'not-found' | 'unavailable' | 'limited';

const STEPS: { step: BookingStep; key: MessageKey }[] = [
  { step: 'services', key: 'booking.step.services' },
  { step: 'slot', key: 'booking.step.slot' },
  { step: 'contact', key: 'booking.step.contact' },
];

/** The page state a failed public request leads to; null when the page stays and shows the message. */
export function pageStateOf(err: unknown): PageState | null {
  const problem = problemOf(err);
  if (problem?.status === 429) return 'limited';
  if (problem?.status !== 404) return null;
  if (problem.code === 'add-on-not-available') return 'unavailable';
  return problem.code === 'workshop-not-found' || !problem.code ? 'not-found' : null;
}

/** The full address of a booking's status page. */
export function statusLink(origin: string, slug: string, token: string): string {
  return `${origin.replace(/\/$/, '')}/w/${encodeURIComponent(slug)}/booking/${encodeURIComponent(token)}`;
}

/**
 * Public booking page `/w/{slug}/book` (add-on `booking`): choose services, a day and a drop-off time, leave the
 * contact details and get the status link. No sign-in; the workshop's front desk confirms.
 */
@Component({
  selector: 'app-public-booking',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, MoneyPipe, Alert, Icon, PublicPage, SlotPicker],
  styles: `
    .steps {
      display: flex;
      gap: 6px;
      padding: 0;
      margin: 0 0 1.25rem;
      list-style: none;
      counter-reset: step;
    }
    .steps li {
      flex: 1 1 0;
      min-width: 0;
      padding-top: 8px;
      border-top: 4px solid var(--bs-border-color);
      font-size: 0.8125rem;
      color: var(--bs-secondary-color);
      counter-increment: step;
    }
    .steps li::before {
      content: counter(step) '. ';
      font-weight: 650;
    }
    .steps li.is-done {
      border-top-color: var(--bs-primary-border-subtle);
    }
    .steps li[aria-current='step'] {
      border-top-color: var(--bs-primary);
      color: var(--bs-body-color);
      font-weight: 600;
    }
    .service-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
      gap: 10px;
    }
    .service-card {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      height: 100%;
      padding: 14px;
      border: 1px solid var(--bs-border-color);
      border-radius: var(--bs-border-radius-lg);
      background: var(--asp-surface);
      cursor: pointer;
    }
    .service-card:has(:checked) {
      border-color: var(--bs-primary);
      box-shadow: inset 0 0 0 1px var(--bs-primary);
    }
    .service-card:has(:focus-visible) {
      outline: 2px solid var(--asp-focus);
      outline-offset: 2px;
    }
    .service-card .form-check-input {
      flex-shrink: 0;
      margin-top: 2px;
    }
    .price {
      font-weight: 650;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .hp {
      position: absolute;
      left: -10000px;
      width: 1px;
      height: 1px;
      overflow: hidden;
    }
    .ticket {
      border-top: 4px solid var(--bs-primary);
    }
    .ticket-number {
      font-size: 2.25rem;
      font-weight: 650;
      font-stretch: 75%;
      line-height: 1;
    }
  `,
  template: `
    <app-public-page [slug]="slug()">
      <div class="card shadow-sm">
        <div class="card-body">
          @switch (state()) {
            @case ('ok') {
              @if (step() === 'done' && created(); as c) {
                <div class="ticket pt-3">
                  <h1 #heading class="mb-3" tabindex="-1">{{ 'booking.done.title' | t }}</h1>
                  <p class="mb-1 text-body-secondary small">{{ 'booking.done.numberLabel' | t }}</p>
                  <p class="ticket-number mb-3">{{ c.number }}</p>
                  @if (slot(); as s) {
                    <p class="d-flex align-items-center gap-2 fw-semibold mb-2">
                      <app-icon name="clock" [size]="18" />{{ 'booking.done.when' | t: { date: dayLabel(s.date), time: s.time } }}
                    </p>
                  }
                  <p class="text-body-secondary">{{ 'booking.done.body' | t }}</p>
                  <label class="form-label fw-semibold" for="bk-link">{{ 'booking.link.label' | t }}</label>
                  <div class="input-group mb-2">
                    <input id="bk-link" class="form-control small" type="text" readonly [value]="link()" (focus)="$any($event.target).select()" />
                    <button type="button" class="btn btn-primary" (click)="copy()">{{ (copied() ? 'booking.link.copied' : 'booking.link.copy') | t }}</button>
                  </div>
                  <p class="form-text mb-3">{{ 'booking.link.hint' | t }}</p>
                  <a class="btn btn-outline-secondary" [routerLink]="['/w', slug(), 'booking', c.statusToken]">{{ 'booking.link.open' | t }}</a>
                </div>
              } @else {
                <h1 class="mb-1">{{ 'booking.public.title' | t }}</h1>
                <p class="text-body-secondary mb-3">{{ 'booking.public.intro' | t }}</p>
                <ol class="steps" [attr.aria-label]="'booking.steps' | t">
                  @for (s of steps; track s.step; let i = $index) {
                    <li [attr.aria-current]="s.step === step() ? 'step' : null" [class.is-done]="i < stepIndex()">{{ s.key | t }}</li>
                  }
                </ol>
                <p class="visually-hidden" aria-live="polite">{{ announcement() }}</p>

                @if (error()) {
                  <app-alert>{{ error() }}</app-alert>
                }

                @switch (step()) {
                  @case ('services') {
                    <h2 #heading class="h5 mb-1" tabindex="-1">{{ 'booking.services.title' | t }}</h2>
                    <p class="text-body-secondary small">{{ 'booking.services.hint' | t }}</p>
                    @if (services(); as list) {
                      @if (list.length) {
                        <div class="service-grid" role="group" [attr.aria-label]="'booking.step.services' | t">
                          @for (s of list; track s.id) {
                            <label class="service-card" [for]="'bk-svc-' + s.id">
                              <input type="checkbox" class="form-check-input" [id]="'bk-svc-' + s.id" [checked]="isSelected(s.id)" (change)="toggle(s.id)" />
                              <span class="d-flex flex-column flex-grow-1 gap-1 overflow-hidden">
                                <span class="d-flex justify-content-between gap-2">
                                  <span class="fw-semibold text-break">{{ s.name }}</span>
                                  <span class="price">{{ s.priceWithVatCents | money }}</span>
                                </span>
                                @if (s.description) {
                                  <span class="small text-body-secondary text-break">{{ s.description }}</span>
                                }
                                <span class="d-flex flex-wrap gap-2 align-items-center small text-body-secondary">
                                  <span class="d-inline-flex align-items-center gap-1"><app-icon name="clock" [size]="14" />{{ duration(s.durationMinutes) }}</span>
                                  @if (s.category) {
                                    <span class="badge bg-secondary-subtle text-secondary-emphasis fw-normal">{{ s.category }}</span>
                                  }
                                </span>
                              </span>
                            </label>
                          }
                        </div>
                      } @else {
                        <div class="d-flex flex-column align-items-center gap-2 py-4 text-center text-body-secondary">
                          <app-icon name="inbox" [size]="36" class="text-body-tertiary" />
                          <p class="fw-semibold text-body mb-0">{{ 'booking.services.empty' | t }}</p>
                        </div>
                      }
                    } @else {
                      <div class="service-grid placeholder-glow" aria-hidden="true">
                        <span class="placeholder placeholder-block w-100"></span>
                        <span class="placeholder placeholder-block w-100"></span>
                      </div>
                    }
                    @if (servicesTouched() && !selected().length) {
                      <p class="invalid-feedback d-block mt-2" role="alert"><app-icon name="alert-circle" [size]="14" /> {{ 'booking.services.choose' | t }}</p>
                    }
                  }
                  @case ('slot') {
                    <h2 #heading class="h5 mb-1" tabindex="-1">{{ 'booking.slot.title' | t }}</h2>
                    <p class="text-body-secondary small">{{ 'booking.slot.hint' | t }}</p>
                    @if (slotNotice()) {
                      <app-alert tone="warning">{{ slotNotice() }}</app-alert>
                    }
                    <app-slot-picker [source]="slotSource" [value]="slot()?.start ?? null" [startDay]="slot()?.date ?? null" (picked)="pickSlot($event)" (failed)="onFailure($event)" />
                    @if (slotTouched() && !slot()) {
                      <p class="invalid-feedback d-block mt-2" role="alert"><app-icon name="alert-circle" [size]="14" /> {{ 'booking.slot.choose' | t }}</p>
                    }
                  }
                  @case ('contact') {
                    <h2 #heading class="h5 mb-3" tabindex="-1">{{ 'booking.contact.title' | t }}</h2>
                    <form id="bk-contact" [formGroup]="form" (ngSubmit)="submit()" novalidate>
                      <div class="row g-3">
                        <div class="col-12">
                          <label class="form-label" for="bk-name">{{ 'common.name' | t }}</label>
                          <input id="bk-name" class="form-control" formControlName="name" autocomplete="name" [class.is-invalid]="!!message('name')" [attr.aria-invalid]="!!message('name')" aria-describedby="bk-name-help" />
                          @if (message('name'); as m) {
                            <div class="invalid-feedback" id="bk-name-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                          }
                        </div>
                        <div class="col-sm-6">
                          <label class="form-label" for="bk-phone">{{ 'common.phone' | t }}</label>
                          <input id="bk-phone" class="form-control" formControlName="phone" type="tel" autocomplete="tel" [class.is-invalid]="contactMissing()" aria-describedby="bk-contact-help" />
                        </div>
                        <div class="col-sm-6">
                          <label class="form-label" for="bk-email">{{ 'common.email' | t }}</label>
                          <input id="bk-email" class="form-control" formControlName="email" type="email" autocomplete="email" [class.is-invalid]="contactMissing() || !!message('email')" [attr.aria-invalid]="!!message('email')" aria-describedby="bk-contact-help" />
                          @if (message('email'); as m) {
                            <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                          }
                        </div>
                        <div class="col-12 mt-1">
                          @if (contactMissing()) {
                            <div class="invalid-feedback d-block mt-0" id="bk-contact-help"><app-icon name="alert-circle" [size]="14" /> {{ 'booking.contact.phoneOrEmail' | t }}</div>
                          } @else {
                            <div class="form-text mt-0" id="bk-contact-help">{{ 'booking.contact.phoneOrEmail' | t }}</div>
                          }
                        </div>
                        <div class="col-sm-6">
                          <label class="form-label" for="bk-plate">{{ 'vehicles.plate' | t }}</label>
                          <input id="bk-plate" class="form-control plate-input" formControlName="plate" autocapitalize="characters" spellcheck="false" autocomplete="off" [placeholder]="'booking.contact.platePlaceholder' | t" [class.is-invalid]="!!message('plate')" [attr.aria-invalid]="!!message('plate')" aria-describedby="bk-plate-help" />
                          @if (message('plate'); as m) {
                            <div class="invalid-feedback" id="bk-plate-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                          }
                        </div>
                        <div class="col-12">
                          <label class="form-label" for="bk-note">{{ 'booking.contact.note' | t }}</label>
                          <textarea id="bk-note" class="form-control" formControlName="note" rows="3" [attr.maxlength]="noteMax" [placeholder]="'booking.contact.notePlaceholder' | t" [class.is-invalid]="!!message('note')"></textarea>
                          @if (message('note'); as m) {
                            <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                          }
                        </div>
                        <div class="hp" aria-hidden="true">
                          <!-- Hidden trap for bots (sent as "website"): a name no browser autofills, never focused by keyboard. -->
                          <label for="bk-hp-x9">{{ 'booking.contact.website' | t }}</label>
                          <input id="bk-hp-x9" name="bk-hp-x9" type="text" formControlName="website" tabindex="-1" autocomplete="new-password" />
                        </div>
                        <div class="col-12">
                          <div class="form-check">
                            <input id="bk-privacy" class="form-check-input" type="checkbox" formControlName="privacyAccepted" [class.is-invalid]="!!message('privacyAccepted')" [attr.aria-invalid]="!!message('privacyAccepted')" aria-describedby="bk-privacy-help" />
                            <label class="form-check-label" for="bk-privacy">{{ 'booking.contact.privacy' | t }}</label>
                            @if (message('privacyAccepted'); as m) {
                              <div class="invalid-feedback" id="bk-privacy-help"><app-icon name="alert-circle" [size]="14" /> {{ m | t }}</div>
                            }
                          </div>
                          <button type="button" class="btn btn-link btn-sm px-0" aria-controls="bk-privacy-notice" [attr.aria-expanded]="privacyOpen()" (click)="privacyOpen.set(!privacyOpen())">
                            {{ 'booking.contact.privacyShow' | t }}
                          </button>
                          @if (privacyOpen()) {
                            <p id="bk-privacy-notice" class="small text-body-secondary bg-body-tertiary rounded p-3 mb-0">{{ 'booking.contact.privacyNotice' | t: { workshop: workshopName() } }}</p>
                          }
                        </div>
                        <div class="col-12">
                          <div class="form-check">
                            <input id="bk-marketing" class="form-check-input" type="checkbox" formControlName="marketingConsent" />
                            <label class="form-check-label" for="bk-marketing">{{ 'booking.contact.marketing' | t }}</label>
                          </div>
                        </div>
                      </div>
                    </form>
                  }
                }

                <section class="border-top mt-4 pt-3" [attr.aria-label]="'booking.summary.title' | t">
                  <div class="d-flex flex-wrap align-items-baseline justify-content-between gap-2">
                    <span class="small text-body-secondary">
                      {{ 'booking.services.selected' | t: { count: selected().length } }}
                      @if (selected().length) {
                        · {{ duration(totalMinutes()) }}
                      }
                      @if (slot(); as s) {
                        · {{ 'booking.done.when' | t: { date: dayLabel(s.date), time: s.time } }}
                      }
                    </span>
                    <span class="d-flex align-items-baseline gap-2">
                      <span class="small text-body-secondary">{{ 'booking.services.total' | t }}</span>
                      <span class="price fs-5">{{ totalCents() | money }}</span>
                    </span>
                  </div>
                  <div class="d-flex flex-wrap justify-content-between gap-2 mt-3">
                    @if (step() !== 'services') {
                      <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="back()"><app-icon name="chevron-left" [size]="16" />{{ 'booking.back' | t }}</button>
                    } @else {
                      <span></span>
                    }
                    @if (step() === 'contact') {
                      <button type="submit" form="bk-contact" class="btn btn-primary" [disabled]="busy()" [attr.aria-busy]="busy()">
                        @if (busy()) {
                          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                        }
                        {{ 'booking.submit' | t }}
                      </button>
                    } @else {
                      <button type="button" class="btn btn-primary" [disabled]="!services()?.length" (click)="next()">{{ 'booking.next' | t }}<app-icon name="arrow-right" [size]="16" /></button>
                    }
                  </div>
                </section>
              }
            }
            @default {
              <div class="d-flex flex-column align-items-center gap-2 py-4 text-center" role="status">
                <app-icon [name]="state() === 'limited' ? 'clock' : 'info'" [size]="36" class="text-body-tertiary" />
                <h1 #heading class="h4 mb-0" tabindex="-1">{{ stateTitle() | t }}</h1>
                <p class="text-body-secondary mb-0">{{ stateBody() | t }}</p>
                @if (state() === 'limited') {
                  <button type="button" class="btn btn-outline-secondary mt-2" (click)="retry()"><app-icon name="refresh" [size]="16" />{{ 'booking.retry' | t }}</button>
                }
              </div>
            }
          }
        </div>
      </div>
    </app-public-page>
  `,
})
export class PublicBooking {
  private readonly api = inject(BookingApi);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly page = viewChild(PublicPage);

  /** The workshop, from the route `/w/:slug/book`. */
  readonly slug = input.required<string>();

  protected readonly steps = STEPS;
  protected readonly noteMax = NOTE_MAX;
  protected readonly state = signal<PageState>('ok');
  protected readonly step = signal<BookingStep>('services');
  protected readonly services = signal<PublicService[] | null>(null);
  protected readonly selected = signal<string[]>([]);
  protected readonly slot = signal<PickedSlot | null>(null);
  protected readonly created = signal<BookingCreated | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly slotNotice = signal<string | null>(null);
  protected readonly servicesTouched = signal(false);
  protected readonly slotTouched = signal(false);
  protected readonly privacyOpen = signal(false);
  protected readonly copied = signal(false);
  protected readonly announcement = signal('');
  /** Mirrors the form's status so the OnPush view follows touched / invalid. */
  private readonly formTick = signal(0);
  protected readonly form = contactForm();

  protected readonly stepIndex = computed(() => STEPS.findIndex((s) => s.step === this.step()));
  private readonly chosen = computed(() => {
    const ids = new Set(this.selected());
    return (this.services() ?? []).filter((s) => ids.has(s.id));
  });
  protected readonly totalCents = computed(() => this.chosen().reduce((sum, s) => sum + s.priceWithVatCents, 0));
  protected readonly totalMinutes = computed(() => this.chosen().reduce((sum, s) => sum + s.durationMinutes, 0));
  protected readonly link = computed(() => {
    const c = this.created();
    return c ? statusLink(this.document.location?.origin ?? '', this.slug(), c.statusToken) : '';
  });
  protected readonly stateTitle = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'booking.limited.title' : this.state() === 'unavailable' ? 'booking.unavailable.title' : 'booking.notFound.title',
  );
  protected readonly stateBody = computed<MessageKey>(() =>
    this.state() === 'limited' ? 'booking.limited.body' : this.state() === 'unavailable' ? 'booking.unavailable.body' : 'booking.notFound.body',
  );

  /** Slots of this workshop, for the picker. */
  protected readonly slotSource = (from: string, days: number) => this.api.slots(this.slug(), from, days);

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => this.loadServices(slug));
    });
    this.form.events.pipe(takeUntilDestroyed()).subscribe(() => this.formTick.update((n) => n + 1));
  }

  protected workshopName(): string {
    return this.page()?.branding()?.name ?? '';
  }

  protected isSelected(id: string): boolean {
    return this.selected().includes(id);
  }

  protected toggle(id: string): void {
    this.selected.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  protected duration(minutes: number): string {
    const d = durationKey(minutes);
    return this.i18n.t(d.key, d.params);
  }

  protected dayLabel(date: string): string {
    return formatDay(date, this.i18n.lang(), { weekday: 'long', day: 'numeric', month: 'long' });
  }

  protected pickSlot(slot: PickedSlot): void {
    this.slot.set(slot);
    this.slotNotice.set(null);
    this.go('contact');
  }

  next(): void {
    this.error.set(null);
    if (this.step() === 'services') {
      this.servicesTouched.set(true);
      if (this.selected().length) this.go('slot');
    } else if (this.step() === 'slot') {
      this.slotTouched.set(true);
      if (this.slot()) this.go('contact');
    }
  }

  protected back(): void {
    this.error.set(null);
    this.go(this.step() === 'contact' ? 'slot' : 'services');
  }

  protected message(field: 'name' | 'email' | 'plate' | 'note' | 'privacyAccepted'): MessageKey | null {
    this.formTick();
    return fieldMessage(this.form.controls[field], {
      required: field === 'name' ? 'booking.contact.nameRequired' : field === 'plate' ? 'booking.contact.plateRequired' : 'booking.contact.privacyRequired',
      email: 'customers.emailInvalid',
      maxlength: 'booking.contact.tooLong',
    });
  }

  protected contactMissing(): boolean {
    this.formTick();
    const f = this.form;
    return f.hasError('phoneOrEmail') && (f.controls.phone.touched || f.controls.email.touched);
  }

  submit(): void {
    const slot = this.slot();
    if (!slot) {
      this.go('slot');
      return;
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.error.set(this.i18n.t('error.validation'));
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.requestBooking(this.slug(), toBookingRequest(this.selected(), slot.start, this.form, this.i18n.lang())).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.created.set(created);
        this.go('done');
      },
      error: (e: unknown) => {
        this.busy.set(false);
        if (problemCode(e) === 'slot-unavailable') {
          // Taken in the meantime: back to the times, freshly loaded.
          this.slot.set(null);
          this.slotNotice.set(this.i18n.t('booking.slot.taken'));
          this.go('slot');
          return;
        }
        const state = pageStateOf(e);
        if (state === 'limited') {
          this.error.set(`${this.i18n.t('booking.limited.title')}. ${this.i18n.t('booking.limited.body')}`);
          return;
        }
        if (state) {
          this.state.set(state);
          this.focusHeading();
          return;
        }
        const marked = applyProblem(this.form, e, { 'privacy-consent-required': 'privacyAccepted', 'invalid-plate': 'plate' });
        this.error.set(marked && problemCode(e) !== 'validation' ? null : this.i18n.error(e));
      },
    });
  }

  protected onFailure(e: unknown): void {
    const state = pageStateOf(e);
    if (state) {
      this.state.set(state);
      this.focusHeading();
    }
  }

  protected retry(): void {
    this.state.set('ok');
    if (!this.services()) this.loadServices(this.slug());
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.link());
      this.copied.set(true);
    } catch {
      // No clipboard access: select the text so it can be copied by hand.
      (this.document.getElementById('bk-link') as HTMLInputElement | null)?.select();
    }
  }

  private go(step: BookingStep): void {
    this.step.set(step);
    const index = STEPS.findIndex((s) => s.step === step);
    this.announcement.set(
      step === 'done' ? this.i18n.t('booking.done.title') : this.i18n.t('booking.step.of', { current: index + 1, total: STEPS.length, name: this.i18n.t(STEPS[index].key) }),
    );
    this.focusHeading();
  }

  /** Moves focus to the new step's heading, so keyboard and screen reader users start there. */
  private focusHeading(): void {
    setTimeout(() => this.heading()?.nativeElement.focus());
  }

  private loadServices(slug: string): void {
    this.services.set(null);
    this.api.publicServices(slug).subscribe({
      next: (list) => this.services.set(list),
      error: (e: unknown) => {
        const state = pageStateOf(e);
        if (state) this.state.set(state);
        else this.error.set(this.i18n.error(e));
      },
    });
  }
}
