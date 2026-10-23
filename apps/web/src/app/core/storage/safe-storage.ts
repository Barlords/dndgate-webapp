import { Injectable } from '@angular/core';

/** `localStorage` that never throws (private windows, blocked site data, quota). */
@Injectable({ providedIn: 'root' })
export class SafeStorage {
  get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  set(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Storage unavailable: the value just will not survive a reload.
    }
  }

  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      // Nothing to remove from an unavailable storage.
    }
  }
}
