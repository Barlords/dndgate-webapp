import { TestBed } from '@angular/core/testing';
import { TokenStore } from './token-store';

describe('TokenStore', () => {
  beforeEach(() => localStorage.clear());

  it('keeps the access token in memory and the refresh token in storage', () => {
    const store = TestBed.inject(TokenStore);
    store.save({ token: 'a1', refreshToken: 'r1' });

    expect(store.accessToken()).toBe('a1');
    expect(store.refreshToken()).toBe('r1');
    expect(localStorage.getItem('dg.refresh')).toBe('r1');
    expect(localStorage.getItem('dg.access')).toBeNull();
  });

  it('clears both tokens', () => {
    const store = TestBed.inject(TokenStore);
    store.save({ token: 'a1', refreshToken: 'r1' });
    store.clear();

    expect(store.accessToken()).toBeNull();
    expect(store.refreshToken()).toBeNull();
  });
});
