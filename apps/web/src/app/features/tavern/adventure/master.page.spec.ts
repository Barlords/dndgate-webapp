import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { MasterClaimDto } from '../../../core/api/generated/models/master-claim-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  method: string;
  url: string;
}

const claimDto = (overrides: Partial<MasterClaimDto> = {}): MasterClaimDto => ({
  id: 'c1',
  adventureId: 'a1',
  candidateId: 'p1',
  status: 'OPEN',
  expiresAt: '2026-11-01T10:00:00Z',
  voters: [{ accountId: 'me' }, { accountId: 'p2', approved: true }],
  ...overrides,
});

describe('MasterPage', () => {
  let tavern: TavernTestbed;

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const at = (method: string, path: RegExp) => (r: Req) => r.method === method && path.test(r.url);
  const invitation = at('POST', /\/adventures\/a1\/master\/invitation$/);
  const revoke = at('DELETE', /\/adventures\/a1\/master\/invitation$/);
  const stepDown = at('POST', /\/adventures\/a1\/master\/step-down$/);
  const getClaim = at('GET', /\/adventures\/a1\/master\/claim$/);
  const claim = at('POST', /\/adventures\/a1\/master\/claim$/);
  const cancel = at('DELETE', /\/adventures\/a1\/master\/claim$/);
  const vote = at('PUT', /\/adventures\/a1\/master\/claim\/vote$/);

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/master');
    tavern.backend.expectOne(getAdventure).flush(adventure({ creatorId: 'me', ...overrides }));
    await tavern.render();
  }

  /** A vacant seat: the reader is a player and the vote request goes out. */
  async function openVacant(answer: MasterClaimDto | null, overrides: Partial<AdventureDto> = {}) {
    await open({
      creatorId: 'gone',
      masterVacant: true,
      players: ['me', 'p1', 'p2'],
      ...overrides,
    });
    const request = tavern.backend.expectOne(getClaim);
    if (answer) {
      request.flush(answer);
    } else {
      request.flush({}, { status: 404, statusText: 'Not Found' });
    }
    await tavern.render();
    tavern.backend
      .match((r) => /\/accounts\/id\//.test(r.url))
      .forEach((r) => r.flush({ id: 'x', pseudo: `Pseudo ${r.request.url.split('/').pop()}` }));
    await tavern.render();
  }

  /** Lets a settled clipboard promise reach the page. */
  const settle = async () => {
    await Promise.resolve();
    await tavern.render();
  };
  const text = () => tavern.main().textContent ?? '';
  const toasts = () => TestBed.inject(NotifierService).toasts();

  afterEach(() => {
    // Names of the people shown by the adventure shell are not what these tests are about.
    tavern.backend.match((r) => /\/accounts\/id\//.test(r.url));
    tavern.backend.verify();
  });

  it('master can create, show and revoke an invitation', async () => {
    await open();
    expect(text()).not.toContain('Code d’invitation');
    tavern.button('Générer un code').click();
    tavern.backend
      .expectOne(invitation)
      .flush(
        { code: 'MJ-CODE-42', expiresAt: '2026-11-01T10:00:00Z' },
        { status: 201, statusText: 'Created' },
      );
    await tavern.render();
    expect(tavern.main().querySelector('.code')!.textContent).toContain('MJ-CODE-42');
    expect(text()).toContain('Valable jusqu’au');
    expect(text()).toContain('2026');
    expect(text()).toContain('invalidera celui-ci');
    expect(text()).toContain('remplace l’ancien');

    tavern.button('Révoquer l’invitation').click();
    tavern.backend.expectOne(revoke).flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    expect(tavern.main().querySelector('.code')).toBeNull();
    expect(toasts()[0].message).toContain('révoquée');
  });

  it('copies the code, or says it cannot', async () => {
    await open();
    tavern.button('Générer un code').click();
    tavern.backend.expectOne(invitation).flush({ code: 'ZZ', expiresAt: '2026-11-01T10:00:00Z' });
    await tavern.render();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    tavern.button('Copier').click();
    await settle();
    expect(writeText).toHaveBeenCalledWith('ZZ');
    expect(text()).toContain('Code copié.');

    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    tavern.button('Copier').click();
    await settle();
    expect(text()).toContain('Copie impossible');

    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
      configurable: true,
    });
    tavern.button('Copier').click();
    await settle();
    await tavern.render();
    expect(text()).toContain('Copie impossible');
  });

  it('step down requires the adventure name', async () => {
    await open();
    const typeToConfirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
      .mockReturnValue(of('La mine perdue'));
    tavern.button('Quitter le rôle de MJ').click();
    expect(typeToConfirm.mock.calls[0][0]).toMatchObject({
      expected: 'La mine perdue',
      inputType: 'text',
    });
    const request = tavern.backend.expectOne(stepDown);
    expect(request.request.body).toEqual({ confirmation: 'La mine perdue' });
    request.flush(adventure({ creatorId: 'me', masterVacant: true }));
    // The role changed: the adventure is read again.
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ creatorId: 'me', masterVacant: true, players: [] }));
    await tavern.render();
  });

  it('step down does nothing when cancelled', async () => {
    await open();
    vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
    tavern.button('Quitter le rôle de MJ').click();
    tavern.backend.expectNone(stepDown);
  });

  it('a conflict reloads the adventure', async () => {
    await open();
    tavern.button('Générer un code').click();
    tavern.backend.expectOne(invitation).flush({}, { status: 409, statusText: 'Conflict' });
    tavern.backend.expectOne(getAdventure).flush(adventure({ creatorId: 'other' }));
    await tavern.render();
    expect(toasts()[0].message).toContain('modifié');
  });

  it('shows another failure through a notification', async () => {
    await open();
    tavern.button('Générer un code').click();
    tavern.backend.expectOne(invitation).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(toasts()[0].kind).toBe('error');
  });

  it('a player can claim only when the seat is vacant', async () => {
    await open({ creatorId: 'master', players: ['me'] });
    expect(text()).not.toContain('Demander à devenir MJ');
    tavern.backend.expectNone(getClaim);
  });

  it('a player claims a vacant seat and the vote opens', async () => {
    await openVacant(null);
    tavern.button('Demander à devenir MJ').click();
    tavern.backend
      .expectOne(claim)
      .flush(claimDto({ candidateId: 'me', voters: [{ accountId: 'p1' }] }), {
        status: 201,
        statusText: 'Created',
      });
    tavern.backend.match((r) => /\/accounts\/id\//.test(r.url)).forEach((r) => r.flush({}));
    await tavern.render();
    expect(text()).toContain('Retirer ma demande');
    expect(text()).toContain('En attente');
  });

  it('claiming with no one to vote hands the seat over at once', async () => {
    await openVacant(null);
    tavern.button('Demander à devenir MJ').click();
    tavern.backend.expectOne(claim).flush(claimDto({ status: 'ACCEPTED', candidateId: 'me' }));
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ creatorId: 'me', masterVacant: false, players: ['p1'] }));
    await tavern.render();
    expect(toasts()[0].message).toContain('changé de mains');
  });

  it('voters see approve and refuse, the candidate can cancel', async () => {
    await openVacant(claimDto());
    expect(text()).toContain('Candidat : Pseudo p1');
    const rows = [...tavern.main().querySelectorAll('.voters li')].map((li) =>
      [...li.querySelectorAll('span')].map((span) => span.textContent),
    );
    expect(rows).toEqual([
      ['Pseudo me', 'En attente'],
      ['Pseudo p2', 'Accepte'],
    ]);
    expect(text()).not.toContain('Retirer ma demande');

    tavern.button('Refuser').click();
    const refusal = tavern.backend.expectOne(vote);
    expect(refusal.request.body).toEqual({ approve: false });
    refusal.flush(
      claimDto({
        voters: [
          { accountId: 'me', approved: false },
          { accountId: 'p2', approved: true },
        ],
      }),
    );
    await tavern.render();
    expect(text()).toContain('Refuse');

    // The answer can change while the vote is open.
    tavern.button('Accepter').click();
    expect(tavern.backend.expectOne(vote).request.body).toEqual({ approve: true });
  });

  it('an approval that ends the vote reloads the adventure', async () => {
    await openVacant(claimDto());
    tavern.button('Accepter').click();
    tavern.backend.expectOne(vote).flush(claimDto({ status: 'ACCEPTED' }));
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ creatorId: 'p1', masterVacant: false, players: ['me', 'p2'] }));
    await tavern.render();
    expect(text()).not.toContain('Accepter');
  });

  it('the candidate withdraws the request', async () => {
    await openVacant(claimDto({ candidateId: 'me', voters: [{ accountId: 'p1' }] }));
    expect(text()).not.toContain('Refuser');
    tavern.button('Retirer ma demande').click();
    tavern.backend.expectOne(cancel).flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    expect(text()).toContain('Demander à devenir MJ');
  });

  it('retries a failed vote lookup', async () => {
    await open({ creatorId: 'gone', masterVacant: true, players: ['me'] });
    tavern.backend.expectOne(getClaim).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(text()).toContain('Impossible de charger le vote.');
    tavern.button('Réessayer').click();
    tavern.backend.expectOne(getClaim).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    expect(text()).toContain('Demander à devenir MJ');
  });

  it('nothing is offered on a closed adventure', async () => {
    await open({ status: 'FINISH' });
    expect(text()).toContain('terminée ou abandonnée');
    expect(text()).not.toContain('Générer un code');
    expect(text()).not.toContain('Quitter le rôle de MJ');
  });

  it('nothing is offered on a closed adventure with a vacant seat', async () => {
    await open({ creatorId: 'gone', masterVacant: true, players: ['me'], status: 'ABORT' });
    tavern.backend.expectNone(getClaim);
    expect(text()).not.toContain('Demander à devenir MJ');
  });
});
