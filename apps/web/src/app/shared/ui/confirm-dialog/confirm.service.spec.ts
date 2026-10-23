import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConfirmService } from './confirm.service';

async function settle(): Promise<void> {
  TestBed.tick();
  await TestBed.inject(ApplicationRef).whenStable();
}

function dialog(): HTMLElement {
  const element = document.querySelector<HTMLElement>('dg-confirm-dialog');
  if (!element) {
    throw new Error('No confirm dialog is open');
  }
  return element;
}

function button(label: string): HTMLButtonElement {
  const found = Array.from(dialog().querySelectorAll('button')).find(
    (candidate) => candidate.textContent?.trim() === label,
  );
  if (!found) {
    throw new Error(`No button labelled ${label}`);
  }
  return found;
}

function input(): HTMLInputElement {
  const found = dialog().querySelector('input');
  if (!found) {
    throw new Error('No input in the dialog');
  }
  return found;
}

async function type(text: string): Promise<void> {
  input().value = text;
  input().dispatchEvent(new Event('input'));
  await settle();
}

describe('ConfirmService', () => {
  let service: ConfirmService;

  beforeEach(() => {
    service = TestBed.inject(ConfirmService);
  });

  describe('confirm', () => {
    const options = {
      title: 'Lancer l’aventure',
      message: 'Les joueurs ne pourront plus la rejoindre librement.',
      confirmLabel: 'Lancer',
    };

    it('emits false on cancel', async () => {
      const results: boolean[] = [];
      service.confirm(options).subscribe((result) => results.push(result));
      await settle();

      button('Annuler').click();
      await settle();

      expect(results).toEqual([false]);
      expect(document.querySelector('dg-confirm-dialog')).toBeNull();
    });

    it('emits true on confirm', async () => {
      const results: boolean[] = [];
      service.confirm(options).subscribe((result) => results.push(result));
      await settle();

      expect(dialog().textContent).toContain('Lancer l’aventure');
      expect(dialog().textContent).toContain(
        'Les joueurs ne pourront plus la rejoindre librement.',
      );
      button('Lancer').click();
      await settle();

      expect(results).toEqual([true]);
    });

    it('opens on subscription and closes when the subscriber leaves', async () => {
      const confirmation = service.confirm(options);
      await settle();
      expect(document.querySelector('dg-confirm-dialog')).toBeNull();

      const subscription = confirmation.subscribe();
      await settle();
      expect(dialog().closest('[role="alertdialog"]')).not.toBeNull();

      subscription.unsubscribe();
      await settle();
      expect(document.querySelector('dg-confirm-dialog')).toBeNull();
    });

    it('emits false when closed with Escape', async () => {
      const results: boolean[] = [];
      service.confirm(options).subscribe((result) => results.push(result));
      await settle();

      dialog().dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }),
      );
      await settle();

      expect(results).toEqual([false]);
    });
  });

  describe('typeToConfirm', () => {
    it('keeps confirm disabled until the name matches', async () => {
      const results: (string | null)[] = [];
      service
        .typeToConfirm({
          title: 'Supprimer l’aventure',
          message: 'Retape son nom pour confirmer.',
          expected: 'La Mine perdue',
          inputLabel: 'Nom de l’aventure',
          inputType: 'text',
          confirmLabel: 'Supprimer',
        })
        .subscribe((result) => results.push(result));
      await settle();

      expect(input().type).toBe('text');
      expect(button('Supprimer').disabled).toBe(true);

      await type('La Mine');
      expect(button('Supprimer').disabled).toBe(true);

      await type('la mine perdue');
      expect(button('Supprimer').disabled).toBe(true);

      await type('  La Mine perdue ');
      expect(button('Supprimer').disabled).toBe(false);

      button('Supprimer').click();
      await settle();

      expect(results).toEqual(['  La Mine perdue ']);
    });

    it('accepts the name whatever the accent encoding', async () => {
      const results: (string | null)[] = [];
      service
        .typeToConfirm({
          title: 'Supprimer le grimoire',
          message: 'Retape son nom pour confirmer.',
          expected: 'Pyromancié',
          inputLabel: 'Nom du grimoire',
          inputType: 'text',
          confirmLabel: 'Supprimer',
        })
        .subscribe((result) => results.push(result));
      await settle();

      await type('Pyromancié');

      expect(button('Supprimer').disabled).toBe(false);
    });

    it('returns the typed password unchanged', async () => {
      const results: (string | null)[] = [];
      service
        .typeToConfirm({
          title: 'Supprimer ton compte',
          message: 'Saisis ton mot de passe pour confirmer.',
          inputLabel: 'Mot de passe',
          inputType: 'password',
          confirmLabel: 'Supprimer mon compte',
        })
        .subscribe((result) => results.push(result));
      await settle();

      expect(input().type).toBe('password');
      expect(button('Supprimer mon compte').disabled).toBe(true);

      await type('  s3cret phrase ');
      button('Supprimer mon compte').click();
      await settle();

      expect(results).toEqual(['  s3cret phrase ']);
    });

    it('emits null on cancel', async () => {
      const results: (string | null)[] = [];
      service
        .typeToConfirm({
          title: 'Supprimer ton compte',
          message: 'Saisis ton mot de passe pour confirmer.',
          inputLabel: 'Mot de passe',
          inputType: 'password',
          confirmLabel: 'Supprimer mon compte',
        })
        .subscribe((result) => results.push(result));
      await settle();

      await type('secret');
      button('Annuler').click();
      await settle();

      expect(results).toEqual([null]);
    });
  });
});
