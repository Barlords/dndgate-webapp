import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { provideApi } from '../../core/api/api.providers';
import { AuthService } from '../../core/auth/auth.service';
import { NotifierService } from '../../core/errors/notifier.service';
import { PublicProfilePage } from './public-profile.page';

describe('PublicProfilePage', () => {
  let backend: HttpTestingController;

  async function setup(accountId: string, me = '1') {
    localStorage.clear();
    const params = new BehaviorSubject(convertToParamMap({ accountId }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).reloadAccount().subscribe();
    backend
      .expectOne((r) => /accounts\/me$/.test(r.url))
      .flush({ id: me, pseudo: 'Me', email: 'me@x.fr' });
    const fixture = TestBed.createComponent(PublicProfilePage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  const account = (id: string) => backend.expectOne((r) => r.url.endsWith(`/accounts/id/${id}`));
  const mean = (id: string) =>
    backend.expectOne((r) => r.url.endsWith('/ratings/mean') && r.params.get('accountId') === id);

  afterEach(() => backend.verify());

  it('public profile shows no-rating state', async () => {
    const { fixture, root } = await setup('2');
    account('2').flush({ id: '2', pseudo: 'Bilbo' });
    mean('2').flush({ average: null, count: 0 });
    fixture.detectChanges();
    expect(root.textContent).toContain('Bilbo');
    expect(root.textContent).toContain("Pas encore d'avis");
    expect(root.querySelector('dg-star-rating')).toBeNull();
    backend.expectNone((r) => r.url.endsWith('/ratings'));
  });

  it('public profile shows the average and the count, without the list of someone else', async () => {
    const { fixture, root } = await setup('2');
    account('2').flush({ id: '2', pseudo: 'Bilbo' });
    mean('2').flush({ average: 4.4, count: 12 });
    fixture.detectChanges();
    expect(root.querySelector('dg-star-rating')).not.toBeNull();
    expect(root.textContent).toContain('12 avis');
    expect(root.textContent).not.toContain('Avis reçus');
  });

  it('names an anonymized account', async () => {
    const { fixture, root } = await setup('2');
    account('2').flush({ id: '2', pseudo: 'deleted-ab', anonymized: true });
    mean('2').flush({ average: null, count: 0 });
    fixture.detectChanges();
    expect(root.textContent).toContain('Compte supprimé');
    expect(root.textContent).not.toContain('deleted-ab');
  });

  it('lists my own received ratings with their authors, and pages them', async () => {
    const { fixture, root } = await setup('1', '1');
    account('1').flush({ id: '1', pseudo: 'Me' });
    mean('1').flush({ average: 5, count: 2 });
    const ratings = (page: number) =>
      backend.expectOne((r) => r.url.endsWith('/ratings') && r.params.get('page') === String(page));
    ratings(0).flush({
      content: [{ id: 'r1', emitterId: '8', rate: 5, createdAt: '2026-10-01T10:00:00Z' }],
      page: 0,
      size: 10,
      totalElements: 11,
      totalPages: 2,
    });
    fixture.detectChanges();
    account('8').flush({ id: '8', pseudo: 'Frodo' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Avis reçus');
    expect(root.textContent).toContain('Frodo');
    (root.querySelector('button[aria-label="Page suivante"]') as HTMLButtonElement).click();
    ratings(1).flush({ content: [], page: 1, size: 10, totalElements: 11, totalPages: 2 });
  });

  it('says so when the player does not exist', async () => {
    const { fixture, root } = await setup('404');
    account('404').flush({}, { status: 404, statusText: 'Not Found' });
    backend.match((r) => r.url.endsWith('/ratings/mean'));
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('introuvable');
    expect(TestBed.inject(NotifierService).toasts()).toEqual([]);
  });

  it('reports another failure with a toast', async () => {
    const { fixture } = await setup('2');
    account('2').flush({}, { status: 500, statusText: 'Server Error' });
    backend.match((r) => r.url.endsWith('/ratings/mean'));
    fixture.detectChanges();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Chargement du joueur');
  });
});
