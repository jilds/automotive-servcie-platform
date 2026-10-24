import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { applyProblem, fieldMessage, isStale, shownOnFields } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { BrandingEditor } from '../identity/branding-editor';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { BackofficeApi } from './backoffice-api.service';
import { WorkshopSettings } from './backoffice.models';
import { COUNTRIES, FIELD_CONTROL, SettingsForm, settingsForm, timeZones, toFormValue, toSettings } from './settings-form';
import { Toasts } from './toasts';
import { HasUnsavedChanges } from './unsaved-changes.guard';

/** Workshop settings for the owner: name, NIF, country, time zone, default VAT rate and labour rate. */
@Component({
  selector: 'app-backoffice-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon, BrandingEditor],
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
  template: `
    <header class="mb-4">
      <h1 class="mb-2">{{ 'backoffice.settings.title' | t }}</h1>
      <p class="text-body-secondary mb-0">{{ 'backoffice.settings.subtitle' | t }}</p>
    </header>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    @if (!loaded()) {
      @if (!error()) {
        <div class="card shadow-sm" aria-busy="true">
          <div class="card-body placeholder-glow vstack gap-2" aria-hidden="true">
            <span class="placeholder placeholder-lg col-5"></span>
            <span class="placeholder col-10"></span>
            <span class="placeholder col-8"></span>
          </div>
        </div>
      }
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" class="card shadow-sm" novalidate>
        <div class="card-body">
          <div class="row g-3">
            <div class="col-12">
              <label class="form-label" for="ws-name">{{ 'backoffice.settings.name' | t }}</label>
              <input id="ws-name" class="form-control" formControlName="name" autocomplete="organization" [class.is-invalid]="invalid('name')" />
              @if (invalid('name')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('name', 'backoffice.settings.nameRequired') }}</div>
              }
            </div>
            <div class="col-md-6">
              <label class="form-label" for="ws-nif">{{ 'common.nif' | t }}</label>
              <input id="ws-nif" class="form-control" formControlName="nif" inputmode="numeric" maxlength="9" [class.is-invalid]="invalid('nif')" />
              @if (invalid('nif')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('nif', form.controls.nif.hasError('nif') ? 'common.nifCheckDigit' : 'backoffice.settings.nifInvalid') }}</div>
              } @else {
                <div class="form-text">{{ 'backoffice.settings.nifHelp' | t }}</div>
              }
            </div>
            <div class="col-md-6">
              <label class="form-label" for="ws-country">{{ 'backoffice.settings.country' | t }}</label>
              <select id="ws-country" class="form-select" formControlName="country" [class.is-invalid]="invalid('country')">
                @for (c of countries(); track c.code) {
                  <option [value]="c.code">{{ c.label }}</option>
                }
              </select>
              @if (invalid('country')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('country', 'backoffice.settings.countryInvalid') }}</div>
              }
            </div>
            <div class="col-12">
              <label class="form-label" for="ws-timezone">{{ 'backoffice.settings.timezone' | t }}</label>
              <select id="ws-timezone" class="form-select" formControlName="timezone" [class.is-invalid]="invalid('timezone')">
                @for (z of zones(); track z) {
                  <option [value]="z">{{ z }}</option>
                }
              </select>
              @if (invalid('timezone')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('timezone', 'backoffice.settings.timezoneRequired') }}</div>
              } @else {
                <div class="form-text">{{ 'backoffice.settings.timezoneHelp' | t }}</div>
              }
            </div>
            <div class="col-md-6">
              <label class="form-label" for="ws-vat">{{ 'backoffice.settings.vatRate' | t }}</label>
              <input id="ws-vat" class="form-control" formControlName="defaultVatRate" type="number" inputmode="decimal" min="0" max="100" step="0.01" [class.is-invalid]="invalid('defaultVatRate')" />
              @if (invalid('defaultVatRate')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('defaultVatRate', 'backoffice.settings.vatInvalid') }}</div>
              } @else {
                <div class="form-text">{{ 'backoffice.settings.vatHelp' | t }}</div>
              }
            </div>
            <div class="col-md-6">
              <label class="form-label" for="ws-labour">{{ 'backoffice.settings.labourRate' | t }}</label>
              <input id="ws-labour" class="form-control" formControlName="labourRateEuros" type="number" inputmode="decimal" min="0" step="0.01" [class.is-invalid]="invalid('labourRateEuros')" />
              @if (invalid('labourRateEuros')) {
                <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ message('labourRateEuros', 'backoffice.settings.labourInvalid') }}</div>
              } @else {
                <div class="form-text">{{ 'backoffice.settings.labourHelp' | t }}</div>
              }
            </div>
            <div class="col-12 d-flex flex-wrap justify-content-end gap-2">
              <button type="button" class="btn btn-outline-secondary" [disabled]="!dirty() || busy()" (click)="discard()">{{ 'backoffice.settings.discard' | t }}</button>
              <button type="submit" class="btn btn-primary" [disabled]="!dirty() || busy()" [attr.aria-busy]="busy()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                }
                {{ 'backoffice.settings.save' | t }}
              </button>
            </div>
          </div>
        </div>
      </form>
    }

    <!-- Appearance (Release 3): saved on its own, so it is not part of the unsaved-changes guard. -->
    <app-branding-editor #branding class="d-block mt-4" (versionChange)="adoptVersion($event)" />

    <dialog #leaveDialog class="modal" aria-labelledby="leave-title" (close)="resolveLeave(leaveDialog.returnValue === 'leave')">
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" method="dialog">
          <div class="modal-header">
            <h2 class="modal-title" id="leave-title">{{ 'backoffice.settings.leaveTitle' | t }}</h2>
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
export class Settings implements OnInit, HasUnsavedChanges {
  private readonly api = inject(BackofficeApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  private readonly leaveDialog = viewChild.required<ElementRef<HTMLDialogElement>>('leaveDialog');
  /** Settings and theme are one workshop row with one version: each form passes the new version to the other. */
  private readonly branding = viewChild.required<BrandingEditor>('branding');
  private leaveResolver: ((leave: boolean) => void) | null = null;

  protected readonly form = settingsForm();
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Mirrors `form.dirty` so the OnPush view follows it. */
  protected readonly dirty = signal(false);
  private readonly saved = signal<WorkshopSettings | null>(null);

  protected readonly zones = computed(() => timeZones(this.saved()?.timezone));
  protected readonly countries = computed(() => {
    const names = new Intl.DisplayNames([this.i18n.lang()], { type: 'region' });
    const codes: string[] = [...COUNTRIES];
    const current = this.saved()?.country;
    if (current && !codes.includes(current)) codes.push(current);
    return codes.map((code) => ({ code, label: names.of(code) ?? code })).sort((a, b) => a.label.localeCompare(b.label));
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.dirty.set(this.form.dirty));
  }

  ngOnInit(): void {
    this.api.settings().subscribe({
      next: (s) => this.reset(s),
      error: (e) => this.error.set(this.i18n.error(e)),
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

  protected invalid(control: keyof SettingsForm['controls']): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || c.dirty);
  }

  /** A server problem code with its own message (for example `error.invalid-plate`), else the field's message. */
  protected message(control: keyof SettingsForm['controls'], fallback: MessageKey): string {
    const key = fieldMessage(this.form.controls[control]);
    return this.i18n.t(key && key !== 'error.field' ? key : fallback);
  }

  protected discard(): void {
    const s = this.saved();
    if (s) this.reset(s);
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.dirty.set(this.form.dirty);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.saveSettings(toSettings(this.form, this.saved()?.version ?? 0)).subscribe({
      next: (s) => {
        this.reset(s);
        this.branding().useVersion(s.version);
        this.busy.set(false);
        this.toasts.show(this.i18n.t('backoffice.settings.saved'));
      },
      error: (e) => {
        if (isStale(e)) {
          // Saved elsewhere since this page loaded: show the stored settings and say why.
          this.busy.set(false);
          this.error.set(this.i18n.error(e));
          this.api.settings().subscribe({
            next: (s) => {
              this.reset(s);
              this.branding().useVersion(s.version);
            },
            error: () => {},
          });
          return;
        }
        const marked = applyProblem(this.form, e, { 'invalid-nif': 'nif' }, FIELD_CONTROL);
        this.error.set(shownOnFields(e, marked) ? null : this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }

  /** The theme was saved: the workshop row has a new version, which the next settings save must send. */
  protected adoptVersion(version: number): void {
    this.saved.update((s) => (s ? { ...s, version } : s));
  }

  private reset(s: WorkshopSettings): void {
    this.saved.set(s);
    this.form.reset(toFormValue(s));
    this.dirty.set(false);
    this.loaded.set(true);
  }
}
