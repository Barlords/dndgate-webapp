import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../core/api/api.providers';
import { AuthService } from '../../../core/auth/auth.service';
import { adventure } from '../tavern.testing';
import { AdventureContext } from './adventure-context.service';

describe('AdventureContext', () => {
  let context: AdventureContext;
  let backend: HttpTestingController;

  const get = (r: { method: string; url: string }) =>
    r.method === 'GET' && /\/adventures\/a1$/.test(r.url);

  function setup(me: string | null = 'me'): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        AdventureContext,
        { provide: AuthService, useValue: { account: signal(me ? { id: me } : null) } },
      ],
    });
    context = TestBed.inject(AdventureContext);
    backend = TestBed.inject(HttpTestingController);
  }

  afterEach(() => backend.verify());

  function load(overrides = {}): void {
    context.open('a1');
    backend.expectOne(get).flush(adventure(overrides));
  }

  it('context computes master, player and participant', () => {
    setup('master');
    load({ players: ['p1'] });
    expect(context.isMaster()).toBe(true);
    expect(context.isPlayer()).toBe(false);
    expect(context.isParticipant()).toBe(true);
  });

  it('a player is a participant but not the master', () => {
    setup('p1');
    load();
    expect([context.isMaster(), context.isPlayer(), context.isParticipant()]).toEqual([
      false,
      true,
      true,
    ]);
  });

  it('a stranger is neither', () => {
    setup('stranger');
    load();
    expect([context.isMaster(), context.isPlayer(), context.isParticipant()]).toEqual([
      false,
      false,
      false,
    ]);
  });

  it('the creator has no master rights while the seat is vacant', () => {
    setup('master');
    load({ masterVacant: true });
    expect(context.isMaster()).toBe(false);
    expect(context.isParticipant()).toBe(false);
  });

  it('a 404 means missing, another error means failed', () => {
    setup();
    context.open('a1');
    backend.expectOne(get).flush({}, { status: 404, statusText: 'Not Found' });
    expect(context.state()).toBe('missing');
    context.open('a1');
    backend.expectOne(get).flush({}, { status: 500, statusText: 'Server Error' });
    expect(context.state()).toBe('failed');
  });

  it('a failed reload keeps the adventure shown', () => {
    setup();
    load();
    context.reload();
    backend.expectOne(get).flush({}, { status: 500, statusText: 'Server Error' });
    expect(context.state()).toBe('ready');
    expect(context.adventure()?.name).toBe('La mine perdue');
  });

  it('reload hands the fresh adventure to the callback; update replaces it', () => {
    setup();
    load();
    const seen: (string | undefined)[] = [];
    context.reload((fresh) => seen.push(fresh.name));
    backend.expectOne(get).flush(adventure({ name: 'Neuve' }));
    expect(seen).toEqual(['Neuve']);
    context.update(adventure({ name: 'Encore' }));
    expect(context.adventure()?.name).toBe('Encore');
  });

  it('opening another adventure clears the previous one at once', () => {
    setup();
    load();
    context.open('a1');
    expect(context.adventure()).toBeNull();
    expect(context.state()).toBe('loading');
    backend.expectOne(get).flush(adventure());
  });
});
