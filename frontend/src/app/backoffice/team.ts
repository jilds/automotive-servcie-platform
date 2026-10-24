import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../core/auth/auth.service';
import { Role } from '../core/api/models';
import { isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { CopyLink } from '../identity/copy-link';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { BackofficeApi } from './backoffice-api.service';
import { ROLES, TeamMember } from './backoffice.models';
import { notBlank } from './settings-form';
import { Toasts } from './toasts';

const ROLE_BADGE: Record<Role, string> = {
  OWNER: 'badge bg-primary-subtle text-primary-emphasis',
  FRONT_DESK: 'badge bg-info-subtle text-info-emphasis',
  MECHANIC: 'badge bg-secondary-subtle text-secondary-emphasis',
};

export function roleKey(role: Role): `role.${Role}` {
  return `role.${role}`;
}

function byName(a: TeamMember, b: TeamMember): number {
  return a.name.localeCompare(b.name);
}

/** Team management for the owner: list, invite, change role, deactivate or reactivate. */
@Component({
  selector: 'app-backoffice-team',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, Alert, Icon, CopyLink],
  template: `
    <header class="d-flex flex-wrap align-items-end gap-3 mb-4">
      <div class="me-auto">
        <h1 class="mb-2">{{ 'backoffice.team.title' | t }}</h1>
        <p class="text-body-secondary mb-0">{{ 'backoffice.team.subtitle' | t }}</p>
      </div>
      <div class="d-flex flex-wrap gap-2">
        <button type="button" class="btn btn-outline-secondary" [disabled]="loading()" [attr.aria-busy]="loading()" (click)="load()">
          @if (loading()) {
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
          } @else {
            <app-icon name="refresh" [size]="16" />
          }
          {{ 'common.refresh' | t }}
        </button>
        <button type="button" class="btn btn-primary" (click)="openInvite()"><app-icon name="plus" [size]="16" />{{ 'backoffice.team.invite' | t }}</button>
      </div>
    </header>

    @if (error()) {
      <app-alert>{{ error() }}</app-alert>
    }

    @if (loading() && !loaded()) {
      <div class="placeholder-glow" [attr.aria-busy]="true">
        <span class="placeholder placeholder-block w-100" aria-hidden="true"></span>
      </div>
    } @else {
      <div class="card table-stack mb-4">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>{{ 'common.name' | t }}</th>
                <th>{{ 'common.email' | t }}</th>
                <th>{{ 'backoffice.team.role' | t }}</th>
                <th>{{ 'backoffice.team.status' | t }}</th>
                <th><span class="visually-hidden">{{ 'backoffice.team.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr [attr.data-user]="u.id" [class.row-muted]="!u.active">
                  <td class="cell-title">
                    {{ u.name }}
                    @if (u.id === meId()) {
                      <span class="badge bg-secondary-subtle text-secondary-emphasis ms-1">{{ 'backoffice.team.you' | t }}</span>
                    }
                  </td>
                  <td class="text-break" [attr.data-label]="'common.email' | t">{{ u.email }}</td>
                  <td [attr.data-label]="'backoffice.team.role' | t"><span [class]="roleBadge(u.role)">{{ roleKey(u.role) | t }}</span></td>
                  <td [attr.data-label]="'backoffice.team.status' | t">
                    @if (u.pending) {
                      <span class="badge bg-warning-subtle text-warning-emphasis">{{ 'backoffice.team.pending' | t }}</span>
                    } @else if (u.active) {
                      <span class="badge bg-success-subtle text-success-emphasis">{{ 'backoffice.team.active' | t }}</span>
                    } @else {
                      <span class="badge bg-secondary-subtle text-secondary-emphasis">{{ 'backoffice.team.inactive' | t }}</span>
                    }
                  </td>
                  <td class="cell-actions">
                    @if (u.id !== meId()) {
                      <span class="d-inline-flex flex-wrap align-items-center justify-content-end gap-2">
                        @if (u.pending) {
                          <button type="button" class="btn btn-sm btn-link" [disabled]="busyId() === u.id" (click)="newLink(u)">{{ 'backoffice.team.newLink' | t }}</button>
                        }
                        <select
                          class="form-select form-select-sm w-auto"
                          [value]="u.role"
                          [disabled]="busyId() === u.id"
                          [attr.aria-label]="'backoffice.team.changeRoleOf' | t: { name: u.name }"
                          (change)="changeRole(u, $any($event.target))"
                        >
                          @for (r of roles; track r) {
                            <option [value]="r" [selected]="r === u.role">{{ roleKey(r) | t }}</option>
                          }
                        </select>
                        @if (u.active) {
                          <button type="button" class="btn btn-sm btn-link text-danger-emphasis" [disabled]="busyId() === u.id" (click)="askDeactivate(u)">
                            {{ 'backoffice.team.deactivate' | t }}
                          </button>
                        } @else {
                          <button type="button" class="btn btn-sm btn-link" [disabled]="busyId() === u.id" (click)="setActive(u, true)">
                            {{ 'backoffice.team.activate' | t }}
                          </button>
                        }
                      </span>
                    }
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="cell-full">
                    <div class="d-flex flex-column align-items-center gap-2 py-5 text-center text-body-secondary">
                      <app-icon name="users" [size]="36" class="text-body-tertiary" />
                      <p class="fw-semibold text-body mb-0">{{ 'backoffice.team.empty' | t }}</p>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }

    <dialog #inviteDialog class="modal" aria-labelledby="invite-title">
      <div class="modal-dialog">
        @if (shownLink(); as l) {
          <div class="modal-content">
            <div class="modal-header">
              <h2 class="modal-title" id="invite-title">{{ 'backoffice.team.linkTitle' | t: { name: l.name } }}</h2>
              <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="inviteDialog.close()"></button>
            </div>
            <div class="modal-body vstack gap-3">
              <p class="mb-0">{{ 'backoffice.team.linkBody' | t: { name: l.name } }}</p>
              <app-copy-link [path]="l.path" [expiresAt]="l.expiresAt" [recipient]="l.name" />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-primary" (click)="inviteDialog.close()">{{ 'common.close' | t }}</button>
            </div>
          </div>
        } @else {
        <form class="modal-content" [formGroup]="invite" (ngSubmit)="sendInvite()">
          <div class="modal-header">
            <h2 class="modal-title" id="invite-title">{{ 'backoffice.team.inviteTitle' | t }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="inviteDialog.close()"></button>
          </div>
          <div class="modal-body">
            <div class="row g-3">
              @if (inviteError()) {
                <app-alert class="col-12 mb-0">{{ inviteError() }}</app-alert>
              }
              <div class="col-sm-6">
                <label class="form-label" for="inv-name">{{ 'common.name' | t }}</label>
                <input id="inv-name" class="form-control" formControlName="name" autocomplete="off"
                  [class.is-invalid]="invite.controls.name.invalid && invite.controls.name.touched" />
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="inv-email">{{ 'common.email' | t }}</label>
                <input id="inv-email" class="form-control" formControlName="email" type="email" autocomplete="off"
                  [class.is-invalid]="invite.controls.email.invalid && invite.controls.email.touched" />
              </div>
              <div class="col-sm-6">
                <label class="form-label" for="inv-role">{{ 'backoffice.team.role' | t }}</label>
                <select id="inv-role" class="form-select" formControlName="role">
                  @for (r of roles; track r) {
                    <option [value]="r">{{ roleKey(r) | t }}</option>
                  }
                </select>
              </div>
              <p class="col-12 form-text mb-0">{{ 'backoffice.team.inviteHelp' | t }}</p>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="inviteDialog.close()">{{ 'backoffice.common.cancel' | t }}</button>
            <button type="submit" class="btn btn-primary" [disabled]="invite.invalid || inviting()" [attr.aria-busy]="inviting()">
              @if (inviting()) {
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
              }
              {{ 'backoffice.team.sendInvite' | t }}
            </button>
          </div>
        </form>
        }
      </div>
    </dialog>

    <dialog #confirmDialog class="modal" aria-labelledby="deactivate-title" (close)="pending.set(null)">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title" id="deactivate-title">{{ 'backoffice.team.deactivateTitle' | t: { name: pending()?.name } }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="confirmDialog.close()"></button>
          </div>
          <div class="modal-body"><p class="mb-0">{{ 'backoffice.team.deactivateBody' | t }}</p></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="confirmDialog.close()">{{ 'backoffice.common.cancel' | t }}</button>
            <button type="button" class="btn btn-danger" (click)="confirmDeactivate()">{{ 'backoffice.team.deactivate' | t }}</button>
          </div>
        </div>
      </div>
    </dialog>
  `,
})
export class Team implements OnInit {
  private readonly api = inject(BackofficeApi);
  private readonly i18n = inject(I18nService);
  private readonly toasts = inject(Toasts);
  private readonly auth = inject(AuthService);
  private readonly inviteDialog = viewChild.required<ElementRef<HTMLDialogElement>>('inviteDialog');
  private readonly confirmDialog = viewChild.required<ElementRef<HTMLDialogElement>>('confirmDialog');

  protected readonly roles = ROLES;
  protected readonly roleKey = roleKey;
  protected readonly meId = computed(() => this.auth.user()?.id ?? null);

  protected readonly users = signal<TeamMember[]>([]);
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  /** User whose row is waiting for the server. */
  protected readonly busyId = signal<string | null>(null);
  /** User waiting for the deactivation confirm. */
  protected readonly pending = signal<TeamMember | null>(null);
  protected readonly inviting = signal(false);
  protected readonly inviteError = signal<string | null>(null);
  /** The activation link just issued, shown once in the invite dialog. */
  protected readonly shownLink = signal<{ name: string; path: string; expiresAt: string } | null>(null);

  protected readonly invite = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    role: new FormControl<Role>('MECHANIC', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.users().subscribe({
      next: (list) => {
        this.users.set([...list].sort(byName));
        this.loading.set(false);
        this.loaded.set(true);
      },
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.loading.set(false);
      },
    });
  }

  protected roleBadge(role: Role): string {
    return ROLE_BADGE[role];
  }

  /** A new activation link for a member who has not activated yet; their previous link stops working. */
  protected newLink(user: TeamMember): void {
    this.busyId.set(user.id);
    this.error.set(null);
    this.api.reissueLink(user.id).subscribe({
      next: (link) => {
        this.busyId.set(null);
        this.inviteError.set(null);
        this.shownLink.set({ name: user.name, path: link.activationPath, expiresAt: link.expiresAt });
        showModal(this.inviteDialog().nativeElement);
      },
      error: (e) => {
        this.error.set(this.i18n.error(e));
        this.busyId.set(null);
      },
    });
  }

  protected changeRole(user: TeamMember, select: HTMLSelectElement): void {
    const role = select.value as Role;
    if (role === user.role) return;
    this.update(user, { role, active: user.active }, () => (select.value = user.role), 'backoffice.team.roleChanged');
  }

  protected askDeactivate(user: TeamMember): void {
    this.pending.set(user);
    showModal(this.confirmDialog().nativeElement);
  }

  protected confirmDeactivate(): void {
    const user = this.pending();
    this.confirmDialog().nativeElement.close();
    if (user) this.setActive(user, false);
  }

  protected setActive(user: TeamMember, active: boolean): void {
    this.update(user, { role: user.role, active }, undefined, active ? 'backoffice.team.activated' : 'backoffice.team.deactivated');
  }

  protected openInvite(): void {
    this.invite.reset();
    this.shownLink.set(null);
    this.inviteError.set(null);
    showModal(this.inviteDialog().nativeElement);
  }

  protected sendInvite(): void {
    if (this.invite.invalid) return;
    const v = this.invite.getRawValue();
    this.inviting.set(true);
    this.inviteError.set(null);
    this.api.inviteUser({ name: v.name.trim(), email: v.email.trim(), role: v.role }).subscribe({
      next: (created) => {
        const { activationPath, activationExpiresAt, ...member } = created;
        this.users.update((list) => [...list, member].sort(byName));
        this.inviting.set(false);
        // The link is shown once, here; the server keeps only its hash.
        this.shownLink.set({ name: created.name, path: activationPath, expiresAt: activationExpiresAt });
        this.toasts.show(this.i18n.t('backoffice.team.invited', { name: created.name }));
      },
      error: (e) => {
        this.inviteError.set(this.i18n.error(e));
        this.inviting.set(false);
      },
    });
  }

  private update(
    user: TeamMember,
    body: { role: Role; active: boolean },
    onError: (() => void) | undefined,
    done: 'backoffice.team.roleChanged' | 'backoffice.team.activated' | 'backoffice.team.deactivated',
  ): void {
    this.busyId.set(user.id);
    this.error.set(null);
    this.api.updateUser(user.id, { ...body, version: user.version }).subscribe({
      next: (saved) => {
        this.users.update((list) => list.map((u) => (u.id === saved.id ? saved : u)));
        this.busyId.set(null);
        this.toasts.show(this.i18n.t(done, { name: saved.name, role: this.i18n.t(roleKey(saved.role)) }));
      },
      error: (e) => {
        onError?.();
        this.busyId.set(null);
        if (isStale(e)) {
          // Someone else changed this member since the list loaded: show the team as it is now, then say why.
          this.load();
        }
        this.error.set(this.i18n.error(e));
      },
    });
  }
}

/** Opens a native dialog; test DOMs without `showModal` just get the `open` attribute. */
function showModal(dialog: HTMLDialogElement): void {
  if (typeof dialog.showModal === 'function') dialog.showModal();
  else dialog.setAttribute('open', '');
}
