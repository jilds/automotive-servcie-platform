import { DatePipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { WorkOrder } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import type { MessageKey } from '../core/i18n/messages.en';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { FloorApi } from './floor-api.service';
import { CAPTION_MAX, canChangePhoto, canDeletePhoto, canUploadPhoto, DELETE_REASON_MAX, MAX_PHOTO_BYTES, MAX_PHOTOS, photosLeft, photoStagesFor } from './floor-logic';
import { Photo, PhotoStage } from './floor.models';
import { problemCode, problemOf } from '../core/api/problem';
import { PHOTO_PREPARER } from './image-resize';
import { PhotoCache } from './photo-cache';

/**
 * One picture of the grid. The bytes are fetched (with the token) when the tile scrolls into view, so a long list does
 * not load every photo at once; until then, and when the fetch fails, it shows a placeholder.
 */
@Component({
  selector: 'app-photo-thumb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, TranslatePipe],
  template: `
    <button type="button" class="photo-tile btn p-0 border rounded-3 overflow-hidden position-relative w-100" (click)="opened.emit()"
      [attr.aria-label]="label()">
      @if (url(); as u) {
        <img class="w-100 h-100 object-fit-cover" [src]="u" [alt]="photo().caption ?? ''" loading="lazy" decoding="async" />
      } @else {
        <span class="d-flex w-100 h-100 align-items-center justify-content-center text-body-secondary bg-body-tertiary">
          @if (failed()) {
            <app-icon name="alert-triangle" [size]="24" [label]="'floor.photo.loadFailed' | t" />
          } @else {
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
          }
        </span>
      }
    </button>
  `,
  styles: `
    .photo-tile { aspect-ratio: 4 / 3; }
  `,
})
export class PhotoThumb {
  readonly workOrderId = input.required<string>();
  readonly photo = input.required<Photo>();
  readonly opened = output<void>();

  private readonly cache = inject(PhotoCache);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly i18n = inject(I18nService);
  protected readonly url = signal<string | null>(null);
  protected readonly failed = signal(false);
  protected readonly label = computed(() => this.photo().caption || this.i18n.t(`floor.stage.${this.photo().stage}`));
  private observer: IntersectionObserver | null = null;

  constructor() {
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined') return void this.fetch();
      this.observer = new IntersectionObserver((entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          this.observer?.disconnect();
          void this.fetch();
        }
      }, { rootMargin: '200px' });
      this.observer.observe(this.host.nativeElement);
    });
    inject(DestroyRef).onDestroy(() => this.observer?.disconnect());
  }

  private async fetch(): Promise<void> {
    try {
      this.url.set(await this.cache.load(this.workOrderId(), this.photo().id));
    } catch {
      this.failed.set(true);
    }
  }
}

/**
 * Photos of a work order (every role sees them): a grid per stage, an upload for the people allowed to add (the
 * camera on a phone), and a dialog to see one large, change its stage and caption, or delete it. Pictures are resized
 * to 1600 px in the browser before they go up.
 */
@Component({
  selector: 'app-photo-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe, Alert, Icon, PhotoThumb],
  providers: [PhotoCache],
  styles: `
    .photo-large { max-width: 100%; max-height: 60vh; width: auto; height: auto; object-fit: contain; }
    .photo-body { min-width: 0; }
  `,
  template: `
    <section class="card shadow-sm mb-4" aria-labelledby="photos-title" id="floor-photos">
      <div class="card-body">
        <div class="d-flex flex-wrap align-items-center gap-3 mb-2">
          <h2 class="card-title me-auto mb-0" id="photos-title">{{ 'floor.photo.title' | t }}</h2>
          <span class="badge text-bg-secondary" data-test="count">{{ 'floor.photo.count' | t: { count: photos().length, max: max } }}</span>
        </div>

        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }

        @if (canUpload()) {
          <div class="border rounded-3 p-3 mb-3" data-test="upload">
            <div class="row g-2 align-items-end">
              <div class="col-6 col-md-3">
                <label class="form-label small mb-1" for="ph-stage">{{ 'floor.photo.stage' | t }}</label>
                <select id="ph-stage" class="form-select form-select-sm" [value]="stage()" (change)="stage.set(pickStage($event))">
                  @for (s of stages(); track s) {
                    <option [value]="s" [selected]="s === stage()">{{ stageKey(s) | t }}</option>
                  }
                </select>
              </div>
              <div class="col-6 col-md-9">
                <label class="form-label small mb-1" for="ph-caption">{{ 'floor.photo.caption' | t }}</label>
                <input id="ph-caption" class="form-control form-control-sm" type="text" [attr.maxlength]="captionMax" [value]="caption()"
                  [placeholder]="'floor.photo.captionPlaceholder' | t" (input)="caption.set(text($event))" />
              </div>
              <div class="col-12 d-flex flex-wrap gap-2 mt-2">
                <button type="button" class="btn btn-primary" data-test="camera" [disabled]="busy() || full()" [attr.aria-busy]="busy()" (click)="camera.click()">
                  <app-icon name="camera" [size]="16" />{{ 'floor.photo.take' | t }}
                </button>
                <button type="button" class="btn btn-outline-primary" data-test="choose" [disabled]="busy() || full()" (click)="gallery.click()">
                  <app-icon name="plus" [size]="16" />{{ 'floor.photo.choose' | t }}
                </button>
                <input #camera type="file" class="d-none" accept="image/*" capture="environment" tabindex="-1" aria-hidden="true" data-test="camera-input" (change)="picked($event)" />
                <input #gallery type="file" class="d-none" accept="image/jpeg,image/png,image/webp" tabindex="-1" aria-hidden="true" data-test="file-input" (change)="picked($event)" />
              </div>
            </div>
            @if (stage() === 'CHECK_IN') {
              <p class="form-text mb-0 mt-2" data-test="documents-hint">{{ 'floor.photo.documentsHint' | t }}</p>
            }
            <p class="form-text mb-0 mt-2" data-test="hint">{{ 'floor.photo.hint' | t: { max: max, mb: maxMb } }}</p>
            @if (full()) {
              <p class="small text-warning-emphasis mb-0 mt-1" data-test="full">{{ 'floor.photo.full' | t: { max: max } }}</p>
            }
            @if (busy()) {
              <p class="small text-body-secondary mb-0 mt-1" role="status">{{ (preparing() ? 'floor.photo.preparing' : 'floor.photo.uploading') | t }}</p>
            }
          </div>
        } @else {
          <p class="small text-body-secondary" data-test="view-only">{{ 'floor.photo.viewOnly' | t }}</p>
        }

        @for (s of stages(); track s) {
          @if (byStage()[s].length) {
            <h3 class="h6 mt-3 mb-2">{{ stageKey(s) | t }} <span class="text-body-secondary fw-normal">({{ byStage()[s].length }})</span></h3>
            <div class="row row-cols-2 row-cols-md-4 g-2" [attr.data-stage]="s">
              @for (p of byStage()[s]; track p.id) {
                <div class="col">
                  <app-photo-thumb [workOrderId]="workOrder().id" [photo]="p" (opened)="open(p)" />
                  @if (p.caption) {
                    <div class="small text-truncate mt-1" [title]="p.caption">{{ p.caption }}</div>
                  }
                </div>
              }
            </div>
          }
        }
        @if (!photos().length && !loading()) {
          <p class="small text-body-secondary mb-0" data-test="empty">{{ 'floor.photo.empty' | t }}</p>
        }
      </div>
    </section>

    <dialog #dialog class="modal" aria-labelledby="photo-dialog-title" (close)="selected.set(null)">
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title fs-5" id="photo-dialog-title">{{ selected() ? (stageKey(selected()!.stage) | t) : '' }}</h2>
            <button type="button" class="btn-close" [attr.aria-label]="'common.close' | t" (click)="closeDialog()"></button>
          </div>
          <div class="modal-body photo-body vstack gap-3">
            @if (dialogError()) {
              <app-alert>{{ dialogError() }}</app-alert>
            }
            @if (largeUrl(); as u) {
              <img class="img-fluid rounded-3 mx-auto d-block photo-large" [src]="u" [alt]="selected()?.caption ?? ''" />
            } @else if (largeFailed()) {
              <app-alert tone="warning">{{ 'floor.photo.loadFailed' | t }}</app-alert>
            } @else {
              <div class="text-center py-4"><span class="spinner-border" aria-hidden="true"></span></div>
            }
            @if (selected(); as p) {
              <p class="small text-body-secondary mb-0">
                {{ 'floor.photo.by' | t: { name: p.uploadedByName ?? '—', date: (p.createdAt | date: 'dd/MM/yyyy HH:mm') ?? '' } }}
              </p>
              @if (canChange()) {
                <form [formGroup]="edit" (ngSubmit)="saveEdit(p)" class="vstack gap-3" novalidate data-test="edit-form">
                  <div>
                    <label class="form-label" for="pe-stage">{{ 'floor.photo.stage' | t }}</label>
                    <select id="pe-stage" class="form-select" formControlName="stage">
                      @for (s of stages(); track s) {
                        <option [value]="s">{{ stageKey(s) | t }}</option>
                      }
                    </select>
                  </div>
                  <div>
                    <label class="form-label" for="pe-caption">{{ 'floor.photo.caption' | t }}</label>
                    <input id="pe-caption" class="form-control" formControlName="caption" [attr.maxlength]="captionMax" />
                  </div>
                  <div class="d-flex flex-wrap gap-2 justify-content-between">
                    @if (!canDelete()) {
                      <span></span>
                    } @else if (!confirmDelete()) {
                      <button type="button" class="btn btn-outline-danger" data-test="delete" [disabled]="busy()" (click)="startDelete()">
                        <app-icon name="trash" [size]="16" />{{ 'floor.photo.delete' | t }}
                      </button>
                    } @else {
                      <span class="d-flex flex-column gap-2 flex-grow-1">
                        <span class="small">{{ 'floor.photo.deleteConfirm' | t }}</span>
                        <label class="form-label small mb-0" for="pe-reason">{{ 'floor.photo.deleteReason' | t }}</label>
                        <textarea id="pe-reason" class="form-control form-control-sm" rows="2" [attr.maxlength]="deleteReasonMax" [value]="deleteReason()"
                          [class.is-invalid]="!!deleteReasonError()" [placeholder]="'floor.photo.deleteReasonPlaceholder' | t" (input)="deleteReason.set(text($event))"></textarea>
                        @if (deleteReasonError(); as m) {
                          <div class="invalid-feedback d-block" data-test="delete-reason-error">{{ m | t }}</div>
                        }
                        <span class="d-inline-flex flex-wrap align-items-center gap-2">
                        <button type="button" class="btn btn-danger btn-sm" data-test="delete-confirm" [disabled]="busy()" (click)="remove(p)">{{ 'floor.photo.deleteYes' | t }}</button>
                        <button type="button" class="btn btn-outline-secondary btn-sm" (click)="confirmDelete.set(false)">{{ 'common.cancel' | t }}</button>
                        </span>
                      </span>
                    }
                    <button type="submit" class="btn btn-primary" data-test="save" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'common.save' | t }}</button>
                  </div>
                </form>
              } @else if (p.caption) {
                <p class="mb-0 text-break">{{ p.caption }}</p>
              }
            }
          </div>
        </div>
      </div>
    </dialog>
  `,
})
export class PhotoPanel implements OnDestroy {
  readonly workOrder = input.required<WorkOrder>();

  private readonly api = inject(FloorApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly cache = inject(PhotoCache);
  private readonly prepare = inject(PHOTO_PREPARER);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly deleteReasonMax = DELETE_REASON_MAX;
  protected readonly max = MAX_PHOTOS;
  protected readonly maxMb = MAX_PHOTO_BYTES / (1024 * 1024);
  protected readonly captionMax = CAPTION_MAX;

  protected readonly photos = signal<Photo[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly preparing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly stage = signal<PhotoStage>('WORK');
  protected readonly caption = signal('');

  protected readonly selected = signal<Photo | null>(null);
  protected readonly largeUrl = signal<string | null>(null);
  protected readonly largeFailed = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly edit = new FormGroup({
    stage: new FormControl<PhotoStage>('WORK', { nonNullable: true }),
    caption: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(CAPTION_MAX)] }),
  });

  private readonly orderId = computed(() => this.workOrder().id);
  private readonly actor = computed(() => ({ role: this.auth.account()?.role ?? null, userId: this.auth.account()?.id ?? null }));
  protected readonly canUpload = computed(() => canUploadPhoto(this.actor(), this.workOrder()));
  protected readonly full = computed(() => photosLeft(this.photos().length) === 0);
  protected readonly stages = computed(() => photoStagesFor(this.actor()));
  protected readonly canDelete = computed(() => {
    const p = this.selected();
    return !!p && canDeletePhoto(this.actor(), this.workOrder(), p);
  });
  protected readonly deleteReason = signal('');
  protected readonly deleteReasonError = signal<MessageKey | null>(null);
  protected readonly canChange = computed(() => {
    const p = this.selected();
    return !!p && canChangePhoto(this.actor(), this.workOrder(), p);
  });
  protected readonly byStage = computed(() => {
    const groups: Record<PhotoStage, Photo[]> = { CHECK_IN: [], WORK: [], DELIVERY: [], DOCUMENTS: [] };
    for (const p of this.photos()) groups[p.stage].push(p);
    return groups;
  });

  constructor() {
    // Keyed by the order's id, so a reload of the order (a new object) does not read the photos again.
    effect(() => {
      const id = this.orderId();
      untracked(() => this.load(id));
    });
  }

  ngOnDestroy(): void {
    this.cache.dispose();
  }

  protected stageKey(s: PhotoStage): MessageKey {
    return `floor.stage.${s}`;
  }

  protected pickStage(event: Event): PhotoStage {
    return (event.target as HTMLSelectElement).value as PhotoStage;
  }

  protected text(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  private load(workOrderId: string): void {
    this.loading.set(true);
    this.api.photos(workOrderId).subscribe({
      next: (list) => {
        this.photos.set(list);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.loading.set(false);
        this.error.set(this.i18n.error(e));
      },
    });
  }

  /** A file was picked (camera or gallery): resize it in the browser, then upload it. */
  async picked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.busy() || this.full()) return;
    this.error.set(null);
    this.busy.set(true);
    this.preparing.set(true);
    try {
      const prepared = await this.prepare(file);
      this.preparing.set(false);
      if (!prepared.ok) {
        this.error.set(this.i18n.t(prepared.reason === 'too-large' ? 'error.photo-too-large' : 'floor.photo.unreadable'));
        return;
      }
      const photo = await firstValueFrom(this.api.uploadPhoto(this.workOrder().id, prepared.blob, prepared.name, this.stage(), this.caption().trim() || null));
      this.photos.update((l) => [...l, photo]);
      this.caption.set('');
    } catch (e: unknown) {
      this.error.set(this.i18n.error(e));
      this.load(this.workOrder().id);
    } finally {
      this.preparing.set(false);
      this.busy.set(false);
    }
  }

  protected open(p: Photo): void {
    this.selected.set(p);
    this.largeUrl.set(null);
    this.largeFailed.set(false);
    this.dialogError.set(null);
    this.confirmDelete.set(false);
    this.edit.reset({ stage: p.stage, caption: p.caption ?? '' });
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.showModal === 'function' && !dialog.open) dialog.showModal();
    // Look again with the ETag held: an unchanged photo answers 304 and keeps its URL.
    this.cache.load(this.workOrder().id, p.id, true).then(
      (u) => this.selected()?.id === p.id && this.largeUrl.set(u),
      () => this.selected()?.id === p.id && this.largeFailed.set(true),
    );
  }

  protected closeDialog(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
    this.selected.set(null);
  }

  protected saveEdit(p: Photo): void {
    if (this.edit.invalid || this.busy()) return;
    const { stage, caption } = this.edit.getRawValue();
    this.busy.set(true);
    this.dialogError.set(null);
    this.api.updatePhoto(this.workOrder().id, p.id, { stage, caption: caption.trim() || null, version: p.version }).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.photos.update((l) => l.map((x) => (x.id === saved.id ? saved : x)));
        this.closeDialog();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.dialogError.set(this.i18n.error(e));
      },
    });
  }

  protected startDelete(): void {
    this.deleteReason.set('');
    this.deleteReasonError.set(null);
    this.confirmDelete.set(true);
  }

  protected remove(p: Photo): void {
    const reason = this.deleteReason().trim();
    if (!reason) return void this.deleteReasonError.set('floor.photo.deleteReasonRequired');
    if (reason.length > DELETE_REASON_MAX) return void this.deleteReasonError.set('floor.photo.deleteReasonTooLong');
    this.deleteReasonError.set(null);
    this.busy.set(true);
    this.dialogError.set(null);
    this.api.deletePhoto(this.workOrder().id, p.id, reason).subscribe({
      next: () => {
        this.busy.set(false);
        this.cache.forget(p.id);
        this.photos.update((l) => l.filter((x) => x.id !== p.id));
        this.closeDialog();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        if (problemCode(e) === 'reason-required' || (problemCode(e) === 'validation' && problemOf(e)?.errors?.['reason'])) {
          this.deleteReasonError.set('floor.photo.deleteReasonRequired');
        } else {
          this.dialogError.set(this.i18n.error(e));
        }
      },
    });
  }
}
