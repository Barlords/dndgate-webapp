import { TestBed } from '@angular/core/testing';
import { ApiProblem } from './api-problem';
import { NotifierService, Toast } from './notifier.service';

const problem = (extra: Partial<ApiProblem>): ApiProblem => ({ status: 500, errors: [], ...extra });

describe('NotifierService', () => {
  let notifier: NotifierService;

  beforeEach(() => {
    vi.useFakeTimers();
    notifier = TestBed.inject(NotifierService);
  });

  afterEach(() => vi.useRealTimers());

  it('error ignores 401', () => {
    notifier.error(problem({ status: 401 }));
    expect(notifier.toasts()).toEqual([]);
  });

  it('error toast shows the correlation id', () => {
    notifier.error(problem({ status: 500, detail: 'Boom', correlationId: 'cid-1' }));
    const [toast] = notifier.toasts();
    expect(toast.kind).toBe('error');
    expect(toast.message).toBe('Erreur du serveur.');
    expect(toast.detail).toBe('Boom');
    expect(toast.reference).toBe('cid-1');
  });

  it.each([
    [0, 'Le serveur ne répond pas.'],
    [403, 'Tu n’as pas les droits pour cette action.'],
    [404, 'Introuvable.'],
    [503, 'Erreur du serveur.'],
    [400, 'Une erreur est survenue.'],
  ])('maps status %i to its message', (status, message) => {
    notifier.error(problem({ status }));
    expect(notifier.toasts()[0].message).toBe(message);
  });

  it('does not show the technical network detail', () => {
    notifier.error(problem({ status: 0, detail: 'network' }));
    expect(notifier.toasts()[0].detail).toBeUndefined();
  });

  it('puts the detail after "Conflit :" on a 409', () => {
    notifier.error(problem({ status: 409, detail: 'Already there' }));
    expect(notifier.toasts()[0]).toMatchObject({ message: 'Conflit : Already there' });
    expect(notifier.toasts()[0].detail).toBeUndefined();
    notifier.error(problem({ status: 409 }));
    expect(notifier.toasts()[1].message).toBe('Conflit.');
  });

  it('tells how long to wait on a 429', () => {
    notifier.error(problem({ status: 429, retryAfterSeconds: 7 }));
    expect(notifier.toasts()[0].message).toBe('Trop de tentatives, réessaie dans 7 s.');
    notifier.error(problem({ status: 429 }));
    expect(notifier.toasts()[1].message).toBe('Trop de tentatives, réessaie plus tard.');
  });

  it('prefixes the message with the context', () => {
    notifier.error(problem({ status: 404 }), 'Chargement de l’aventure');
    expect(notifier.toasts()[0].message).toBe('Chargement de l’aventure : Introuvable.');
  });

  it('shows a success toast', () => {
    notifier.success('Enregistré.');
    expect(notifier.toasts()[0]).toMatchObject({ kind: 'success', message: 'Enregistré.' });
  });

  it('closes toasts after 6 seconds', () => {
    notifier.success('Ok');
    vi.advanceTimersByTime(5999);
    expect(notifier.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(notifier.toasts()).toHaveLength(0);
  });

  it('keeps the conflict toast until it is dismissed, and reloads on demand', () => {
    const reload = vi.fn();
    notifier.conflict(reload);
    vi.advanceTimersByTime(60000);
    const [toast] = notifier.toasts();
    expect(toast.message).toBe('Quelqu’un a modifié cet élément entre-temps.');
    notifier.act(toast);
    expect(reload).toHaveBeenCalledOnce();
    expect(notifier.toasts()).toHaveLength(0);
  });

  it('dismisses one toast', () => {
    notifier.success('A');
    notifier.success('B');
    notifier.dismiss(notifier.toasts()[0].id);
    expect(notifier.toasts().map((t: Toast) => t.message)).toEqual(['B']);
  });

  describe('auto-close', () => {
    it('keeps an error that carries a reference until it is dismissed', () => {
      notifier.error(problem({ status: 500, correlationId: 'cid-9' }));
      vi.advanceTimersByTime(60_000);
      expect(notifier.toasts()).toHaveLength(1);
    });

    it('closes other toasts after 6 s', () => {
      notifier.success('Ok');
      notifier.error(problem({ status: 500 }));
      vi.advanceTimersByTime(5999);
      expect(notifier.toasts()).toHaveLength(2);
      vi.advanceTimersByTime(2);
      expect(notifier.toasts()).toHaveLength(0);
    });

    it('pauses while hovered or focused and restarts the 6 s when released', () => {
      notifier.success('Ok');
      const [toast] = notifier.toasts();
      vi.advanceTimersByTime(5000);
      notifier.pause(toast.id);
      vi.advanceTimersByTime(60_000);
      expect(notifier.toasts()).toHaveLength(1);
      notifier.resume(toast.id);
      vi.advanceTimersByTime(5999);
      expect(notifier.toasts()).toHaveLength(1);
      vi.advanceTimersByTime(2);
      expect(notifier.toasts()).toHaveLength(0);
    });

    it('does not arm a timer for a sticky toast on resume', () => {
      notifier.error(problem({ status: 500, correlationId: 'cid-9' }));
      const [toast] = notifier.toasts();
      notifier.pause(toast.id);
      notifier.resume(toast.id);
      vi.advanceTimersByTime(60_000);
      expect(notifier.toasts()).toHaveLength(1);
    });
  });
});
