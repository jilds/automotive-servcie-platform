import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { Toasts } from '../backoffice/toasts';
import { HasUnsavedChanges } from '../backoffice/unsaved-changes.guard';
import { applyProblem, fieldMessage, isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { TenantThemeService } from '../shared/theme.service';
import { ShopApi } from './shop-api.service';
import { HOLD_DAYS_MAX, HOLD_DAYS_MIN, PICKUP_NOTE_MAX, ShopSettings } from './shop.models';

/** The owner's shop settings form: hold days 1–14 (whole), pickup note up to 500 characters. */
export function shopSettingsForm() {
  return new FormGroup({
    holdDays: new FormControl<number | null>(3, [Validators.required, Validators.min(HOLD_DAYS_MIN), Validators.max(HOLD_DAYS_MAX), Validators.pattern(/^\d+$/)]),
    pickupNote: new FormControl('', { nonNullable: true, validators: Validators.maxLength(PICKUP_NOTE_MAX) }),
  });
}

/** The PUT body: a blank note as null. */
export function toShopSettings(form: ReturnType<typeof shopSettingsForm>, version: number): ShopSettings {
  const v = form.getRawValue();
  return { holdDays: Number(v.holdDays), pickupNote: v.pickupNote.trim() || null, version };
}

/** The owner's online shop settings (`/backoffice/shop`): how long orders are kept and the pickup note. */
@Component({
  selector: 'app-shop-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert],
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'shop.settings.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'shop.settings.subtitle' | t }}</p>
      </div>
      @if (publicPath(); as path) {
        <a class="btn btn-outline-secondary" [href]="path" target="_blank" rel="noopener">{{ 'shop.settings.publicPage' | t }}</a>
      }
    </header>

    <div aria-live="polite">
      @if (error()) {
        <app-alert>{{ error() }}</app-alert>
      }
    </div>

    @if (!loaded()) {
      @if (!error()) {
        <div class="card shadow-sm" aria-busy="true">
          <div class="card-body placeholder-glow vstack gap-2" aria-hidden="true">
            <span class="placeholder placeholder-lg col-5"></span>
            <span class="placeholder col-10"></span>
          </div>
        </div>
      }
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <section class="card shadow-sm mb-4">
          <div class="card-body">
            <div class="row g-3">
              <div class="col-sm-6 col-lg-4">
                <label class="form-label" for="ss-hold">{{ 'shop.settings.holdDays' | t }}</label>
                <input id="ss-hold" class="form-control" type="number" inputmode="numeric" [attr.min]="holdMin" [attr.max]="holdMax" step="1" formControlName="holdDays" [class.is-invalid]="!!message('holdDays')" [attr.aria-invalid]="!!message('holdDays')" aria-describedby="ss-hold-help" />
                <div [class]="message('holdDays') ? 'invalid-feedback' : 'form-text'" id="ss-hold-help">{{ (message('holdDays') ?? 'shop.settings.holdDaysHint') | t }}</div>
              </div>
              <div class="col-12">
                <label class="form-label" for="ss-note">{{ 'shop.settings.pickupNote' | t }}</label>
                <textarea id="ss-note" class="form-control" rows="3" [attr.maxlength]="noteMax" formControlName="pickupNote" [placeholder]="'shop.settings.pickupNotePlaceholder' | t" [class.is-invalid]="!!message('pickupNote')" [attr.aria-invalid]="!!message('pickupNote')" aria-describedby="ss-note-help"></textarea>
                <div [class]="message('pickupNote') ? 'invalid-feedback' : 'form-text'" id="ss-note-help">{{ (message('pickupNote') ?? 'shop.settings.pickupNoteHint') | t }}</div>
              </div>
            </div>
            <p class="small text-body-secondary mt-3 mb-0">{{ 'shop.settings.listedHint' | t }}</p>
          </div>
        </section>

        <div class="d-flex flex-wrap justify-content-end gap-2">
          <button type="button" class="btn btn-outline-secondary" [disabled]="!dirty() || busy()" (click)="discard()">{{ 'backoffice.settings.discard' | t }}</button>
          <button type="submit" class="btn btn-primary" [disabled]="!dirty() || busy()" [attr.aria-busy]="busy()">
            @if (busy()) {
              <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
            }
            {{ 'common.save' | t }}
          </button>
        </div>
      </form>
    }

    <dialog #leaveDialog class="modal" aria-labelledby="ss-leave-title" (close)="resolveLeave(leaveDialog.returnValue === 'leave')">
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" method="dialog">
          <div class="modal-header">
            <h2 class="modal-title" id="ss-leave-title">{{ 'backoffice.settings.leaveTitle' | t }}</h2>
          </div>
          <div class="modal-body"><p class="mb-0">{{ 'backoffice.settings.leaveBody' | t }}</p></div>
          <div class="modal-footer">
            <button class="btn btn-outline-secondary" value="stay">{{ 'backoffice.settings.stay' | t }}</button>
            <button class="btn btn-danger" value="leave">{{ 'backoffice.settings.leave' | t }}</button>
          </div>
        </form>
      </div>
    </dialog>
  `,
})
export class ShopSettingsPage implements OnInit, HasUnsavedChanges {
  private readonly api = inject(ShopApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  private readonly theme = inject(TenantThemeService);
  private readonly leaveDialog = viewChild.required<ElementRef<HTMLDialogElement>>('leaveDialog');
  private leaveResolver: ((leave: boolean) => void) | null = null;

  protected readonly holdMin = HOLD_DAYS_MIN;
  protected readonly holdMax = HOLD_DAYS_MAX;
  protected readonly noteMax = PICKUP_NOTE_MAX;
  protected readonly form = shopSettingsForm();
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dirty = signal(false);
  private readonly tick = signal(0);
  private readonly saved = signal<ShopSettings | null>(null);

  protected readonly publicPath = computed(() => {
    const slug = this.theme.branding()?.slug;
    return slug ? `/w/${slug}/shop` : null;
  });

  constructor() {
    this.form.events.pipe(takeUntilDestroyed()).subscribe(() => {
      this.dirty.set(this.form.dirty);
      this.tick.update((n) => n + 1);
    });
  }

  ngOnInit(): void {
    this.api.settings().subscribe({
      next: (s) => this.reset(s),
      error: (e: unknown) => this.error.set(this.i18n.error(e)),
    });
  }

  confirmLeave(): boolean | Promise<boolean> {
    if (!this.form.dirty) return true;
    const dialog = this.leaveDialog().nativeElement;
    if (typeof dialog.showModal !== 'function') return window.confirm(this.i18n.t('backoffice.settings.leaveBody'));
    dialog.returnValue = '';
    dialog.showModal();
    return new Promise((resolve) => (this.leaveResolver = resolve));
  }

  protected resolveLeave(leave: boolean): void {
    this.leaveResolver?.(leave);
    this.leaveResolver = null;
  }

  protected onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty) event.preventDefault();
  }

  protected message(field: 'holdDays' | 'pickupNote'): MessageKey | null {
    this.tick();
    const key = fieldMessage(this.form.controls[field], {
      required: 'shop.settings.holdDaysInvalid',
      min: 'shop.settings.holdDaysInvalid',
      max: 'shop.settings.holdDaysInvalid',
      pattern: 'shop.settings.holdDaysInvalid',
      maxlength: 'shop.settings.pickupNoteTooLong',
    });
    // A server "validation" mark has no field text of its own: use the field's rule.
    if (key === 'error.field') return field === 'holdDays' ? 'shop.settings.holdDaysInvalid' : 'shop.settings.pickupNoteTooLong';
    return key;
  }

  protected discard(): void {
    const s = this.saved();
    if (s) this.reset(s);
  }

  save(): void {
    this.form.markAllAsTouched();
    this.tick.update((n) => n + 1);
    if (this.form.invalid) {
      this.error.set(this.i18n.t('error.validation'));
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.saveSettings(toShopSettings(this.form, this.saved()?.version ?? 0)).subscribe({
      next: (s) => {
        this.reset(s);
        this.busy.set(false);
        this.toasts.show(this.i18n.t('shop.settings.saved'));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(this.i18n.error(e));
        if (isStale(e)) {
          // Saved elsewhere since this page loaded: show the stored settings.
          this.api.settings().subscribe({ next: (s) => this.reset(s), error: () => {} });
        } else {
          applyProblem(this.form, e);
          this.tick.update((n) => n + 1);
        }
      },
    });
  }

  private reset(s: ShopSettings): void {
    this.saved.set(s);
    this.form.reset({ holdDays: s.holdDays, pickupNote: s.pickupNote ?? '' });
    this.dirty.set(false);
    this.loaded.set(true);
    this.tick.update((n) => n + 1);
  }
}
