import { Injectable, signal } from '@angular/core';

import { AlertTone } from '../shared/alert';

export interface Toast {
  id: number;
  tone: AlertTone;
  text: string;
}

const TOAST_MS = 4000;

/** Short-lived messages shown in the backoffice toast region. Provided by the backoffice layout. */
@Injectable()
export class Toasts {
  private nextId = 1;
  readonly items = signal<readonly Toast[]>([]);

  show(text: string, tone: AlertTone = 'success'): void {
    const toast = { id: this.nextId++, tone, text };
    this.items.update((list) => [...list, toast]);
    setTimeout(() => this.dismiss(toast.id), TOAST_MS);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((t) => t.id !== id));
  }
}
