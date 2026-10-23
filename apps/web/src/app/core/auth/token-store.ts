import { inject, Injectable } from '@angular/core';
import { AuthDto } from '../api/generated/models/auth-dto';
import { SafeStorage } from '../storage/safe-storage';

const REFRESH_KEY = 'dg.refresh';

/** Access token in memory only; refresh token persisted so a reload keeps the session. */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly storage = inject(SafeStorage);
  private access: string | null = null;

  accessToken(): string | null {
    return this.access;
  }

  refreshToken(): string | null {
    return this.storage.get(REFRESH_KEY);
  }

  save(auth: AuthDto): void {
    this.access = auth.token ?? null;
    if (auth.refreshToken) {
      this.storage.set(REFRESH_KEY, auth.refreshToken);
    }
  }

  clear(): void {
    this.access = null;
    this.storage.remove(REFRESH_KEY);
  }
}
