import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { BrandingEditor } from '../identity/branding-editor';
import { CopyLink } from '../identity/copy-link';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { AdminApi } from './admin-api.service';
import { ActivationLink, addOnsChanged, AdminWorkshop, Plan, planKey, PLANS, STATUS_BADGE, statusKey, STATUSES, SubscriptionStatus } from './admin.models';

/** One workshop for the platform admin: subscription, owner access, add-ons and theme. Never its business data. */
@Component({
  selector: 'app-workshop-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DatePipe, Alert, Icon, CopyLink, BrandingEditor],
  template: `
    <a routerLink="/admin" class="d-inline-flex align-items-center gap-1 small mb-3"><app-icon name="chevron-left" [size]="16" />{{ 'admin.detail.back' | t }}</a>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    @if (workshop(); as w) {
      <header class="d-flex flex-wrap align-items-center gap-3 mb-4">
        <h1 class="mb-0 me-auto text-break">{{ w.name }}</h1>
        <span [class]="statusBadge[w.status]">{{ statusKey(w.status) | t }}</span>
        @if (w.expired) {
          <span class="badge bg-danger-subtle text-danger-emphasis">{{ 'admin.status.expired' | t }}</span>
        }
      </header>
      @if (w.expired) {
        <app-alert tone="warning">{{ 'admin.status.expiredHelp' | t }}</app-alert>
      }

      @if (notice()) {
        <app-alert tone="success">{{ notice() }}</app-alert>
      }

      <div class="row g-4">
        <div class="col-lg-6">
          <section class="card shadow-sm h-100" aria-labelledby="sub-title">
            <div class="card-body vstack gap-3">
              <h2 class="card-title mb-0" id="sub-title">{{ 'admin.subscription.title' | t }}</h2>
              <div class="row g-3">
                <div class="col-sm-6">
                  <label class="form-label" for="sub-plan">{{ 'admin.workshops.plan' | t }}</label>
                  <select id="sub-plan" class="form-select" [value]="plan()" (change)="plan.set($any($event.target).value)">
                    @for (p of plans; track p) {
                      <option [value]="p" [selected]="p === plan()">{{ planKey(p) | t }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="sub-status">{{ 'admin.workshops.status' | t }}</label>
                  <select id="sub-status" class="form-select" [value]="status()" (change)="status.set($any($event.target).value)">
                    @for (s of statuses; track s) {
                      <option [value]="s" [selected]="s === status()">{{ statusKey(s) | t }}</option>
                    }
                  </select>
                </div>
                <div class="col-sm-6">
                  <label class="form-label" for="sub-until">{{ 'admin.subscription.until' | t }}</label>
                  <input id="sub-until" class="form-control" type="date" [value]="until()" (change)="until.set($any($event.target).value)" />
                </div>
              </div>
              <p class="form-text mb-0">{{ 'admin.subscription.help' | t }}</p>
              <div class="d-flex flex-wrap justify-content-end gap-2 mt-auto">
                @if (w.status === 'TRIAL' || w.status === 'ACTIVE') {
                  <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="setStatus('SUSPENDED')">{{ 'admin.subscription.suspend' | t }}</button>
                } @else {
                  <button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="setStatus('ACTIVE')">{{ 'admin.subscription.reactivate' | t }}</button>
                }
                <button type="button" class="btn btn-primary" [disabled]="busy() || !subscriptionDirty()" (click)="saveSubscription()">{{ 'admin.subscription.save' | t }}</button>
              </div>
            </div>
          </section>
        </div>

        <div class="col-lg-6">
          <section class="card shadow-sm h-100" aria-labelledby="owner-title">
            <div class="card-body vstack gap-3">
              <h2 class="card-title mb-0" id="owner-title">{{ 'admin.owner.title' | t }}</h2>
              <dl class="row mb-0 small">
                <dt class="col-5">{{ 'admin.workshops.owner' | t }}</dt>
                <dd class="col-7 text-break">{{ w.ownerEmail ?? '—' }}</dd>
                <dt class="col-5">{{ 'admin.workshops.users' | t }}</dt>
                <dd class="col-7">{{ w.users }}</dd>
                <dt class="col-5">{{ 'common.nif' | t }}</dt>
                <dd class="col-7">{{ w.nif ?? '—' }}</dd>
                <dt class="col-5">{{ 'admin.workshops.country' | t }}</dt>
                <dd class="col-7">{{ w.country }} · {{ w.timezone }}</dd>
                <dt class="col-5">{{ 'admin.workshops.created' | t }}</dt>
                <dd class="col-7">{{ w.createdAt | date: 'dd/MM/yyyy HH:mm' }}</dd>
                <dt class="col-5">{{ 'branding.slug' | t }}</dt>
                <dd class="col-7 font-monospace"><a [href]="'/w/' + w.slug" target="_blank" rel="noopener">/w/{{ w.slug }}</a></dd>
              </dl>
              <p class="form-text mb-0">{{ 'admin.owner.help' | t }}</p>
              @if (link(); as l) {
                <app-copy-link [path]="l.activationPath" [expiresAt]="l.expiresAt" [recipient]="w.ownerEmail" />
              }
              <div class="d-flex justify-content-end mt-auto">
                <button type="button" class="btn btn-outline-secondary" [disabled]="busy() || !w.ownerEmail" (click)="reissue()">{{ 'admin.owner.reissue' | t }}</button>
              </div>
            </div>
          </section>
        </div>

        <div class="col-lg-6">
          <section class="card shadow-sm h-100" aria-labelledby="addons-title">
            <div class="card-body vstack gap-3">
              <h2 class="card-title mb-0" id="addons-title">{{ 'admin.addOns.title' | t }}</h2>
              <div class="form-check form-switch mb-0">
                <input #bookingSwitch id="addon-booking" class="form-check-input" type="checkbox" role="switch" [checked]="booking()" [disabled]="busy()"
                  (change)="booking.set(bookingSwitch.checked)" aria-describedby="addon-booking-help" />
                <label class="form-check-label" for="addon-booking">{{ 'admin.addOns.booking' | t }}</label>
                <div class="form-text" id="addon-booking-help">{{ 'admin.addOns.bookingHelp' | t }}</div>
              </div>
              <div class="form-check form-switch mb-0">
                <input #shopSwitch id="addon-shop" class="form-check-input" type="checkbox" role="switch" [checked]="shop()" [disabled]="busy()"
                  (change)="shop.set(shopSwitch.checked)" aria-describedby="addon-shop-help" />
                <label class="form-check-label" for="addon-shop">{{ 'admin.addOns.shop' | t }}</label>
                <div class="form-text" id="addon-shop-help">{{ 'admin.addOns.shopHelp' | t }}</div>
              </div>
              <p class="form-text mb-0">{{ 'admin.addOns.help' | t }}</p>
              <div class="d-flex justify-content-end mt-auto">
                <button type="button" class="btn btn-primary" [disabled]="busy() || !addOnsDirty()" (click)="saveAddOns()">{{ 'admin.addOns.save' | t }}</button>
              </div>
            </div>
          </section>
        </div>

        <div class="col-12">
          <app-branding-editor #branding [target]="{ kind: 'workshop', id: w.id }" [allowSlug]="true" (versionChange)="adoptVersion($event)" />
        </div>
      </div>
    } @else if (!error()) {
      <div class="placeholder-glow" aria-busy="true"><span class="placeholder placeholder-lg col-6"></span></div>
    }
  `,
})
export class WorkshopDetail {
  private readonly api = inject(AdminApi);
  protected readonly i18n = inject(I18nService);

  /** From the route `/admin/workshops/:id`. */
  readonly id = input.required<string>();

  protected readonly plans = PLANS;
  protected readonly statuses = STATUSES;
  protected readonly planKey = planKey;
  protected readonly statusKey = statusKey;
  protected readonly statusBadge = STATUS_BADGE;

  protected readonly workshop = signal<AdminWorkshop | null>(null);
  protected readonly plan = signal<Plan>('PRO');
  protected readonly status = signal<SubscriptionStatus>('ACTIVE');
  protected readonly until = signal('');
  protected readonly booking = signal(false);
  protected readonly shop = signal(false);
  protected readonly link = signal<ActivationLink | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Subscription, add-ons and theme are one workshop row with one version. */
  private readonly branding = viewChild<BrandingEditor>('branding');
  protected readonly notice = signal<string | null>(null);

  protected readonly subscriptionDirty = computed(() => {
    const w = this.workshop();
    return !!w && (w.plan !== this.plan() || w.status !== this.status() || (w.subscriptionUntil ?? '') !== this.until());
  });

  protected readonly addOnsDirty = computed(() => {
    const w = this.workshop();
    return !!w && addOnsChanged(w.addOns, { booking: this.booking(), shop: this.shop() });
  });

  constructor() {
    effect(() => this.load(this.id()));
  }

  protected setStatus(status: SubscriptionStatus): void {
    this.status.set(status);
    this.saveSubscription();
  }

  protected saveSubscription(): void {
    const w = this.workshop();
    if (!w) return;
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    const body = { plan: this.plan(), status: this.status(), subscriptionUntil: this.until() || null, version: w.version };
    this.api.changeSubscription(w.id, body).subscribe({
      next: (saved) => {
        // Only the subscription fields: unsaved add-on switches stay as they are.
        this.workshop.set(saved);
        this.resetSubscription(saved);
        this.branding()?.useVersion(saved.version);
        this.busy.set(false);
        this.notice.set(this.i18n.t('admin.subscription.saved', { status: this.i18n.t(statusKey(saved.status)) }));
      },
      error: (e) => this.failed(w.id, e),
    });
  }

  protected saveAddOns(): void {
    const w = this.workshop();
    if (!w) return;
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.changeAddOns(w.id, { booking: this.booking(), shop: this.shop(), version: w.version }).subscribe({
      next: (saved) => {
        this.workshop.set(saved);
        this.resetAddOns(saved);
        this.branding()?.useVersion(saved.version);
        this.busy.set(false);
        this.notice.set(this.i18n.t('admin.addOns.saved'));
      },
      error: (e) => this.failed(w.id, e),
    });
  }

  private failed(id: string, e: unknown): void {
    this.busy.set(false);
    if (isStale(e)) {
      // Changed elsewhere since this page loaded: show the workshop as stored, then say why.
      this.api.workshop(id).subscribe({
        next: (fresh) => {
          this.reset(fresh);
          this.branding()?.useVersion(fresh.version);
        },
        error: () => {},
      });
    }
    this.error.set(this.i18n.error(e));
  }

  protected reissue(): void {
    const w = this.workshop();
    if (!w) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.reissueOwnerLink(w.id).subscribe({
      next: (l) => {
        this.link.set(l);
        this.busy.set(false);
      },
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.busy.set(false);
      },
    });
  }

  /** The theme was saved: the workshop row has a new version, which the next subscription or add-ons change must send. */
  protected adoptVersion(version: number): void {
    this.workshop.update((w) => (w ? { ...w, version } : w));
  }

  private load(id: string): void {
    this.workshop.set(null);
    this.link.set(null);
    this.api.workshop(id).subscribe({
      next: (w) => this.reset(w),
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }

  private reset(w: AdminWorkshop): void {
    this.workshop.set(w);
    this.resetSubscription(w);
    this.resetAddOns(w);
  }

  private resetSubscription(w: AdminWorkshop): void {
    this.plan.set(w.plan);
    this.status.set(w.status);
    this.until.set(w.subscriptionUntil ?? '');
  }

  private resetAddOns(w: AdminWorkshop): void {
    this.booking.set(w.addOns.booking);
    this.shop.set(w.addOns.shop);
  }
}
