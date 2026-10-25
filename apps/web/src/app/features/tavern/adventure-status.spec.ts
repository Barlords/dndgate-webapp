import { fr } from '../../core/i18n/fr';
import { allowedTransitions, isClosed } from './adventure-status';

describe('adventure status', () => {
  it('allowed transitions follow the README state machine', () => {
    expect(allowedTransitions('NOT_STARTED')).toEqual(['ONGOING', 'ABORT']);
    expect(allowedTransitions('ONGOING')).toEqual(['FINISH', 'ABORT']);
    expect(allowedTransitions('FINISH')).toEqual([]);
    expect(allowedTransitions('ABORT')).toEqual([]);
  });

  it('only FINISH and ABORT are closed', () => {
    expect(isClosed('FINISH')).toBe(true);
    expect(isClosed('ABORT')).toBe(true);
    expect(isClosed('NOT_STARTED')).toBe(false);
    expect(isClosed('ONGOING')).toBe(false);
  });

  it('labels every status in French', () => {
    expect(fr.tavern.status).toEqual({
      NOT_STARTED: 'Pas commencée',
      ONGOING: 'En cours',
      FINISH: 'Terminée',
      ABORT: 'Abandonnée',
    });
  });
});
