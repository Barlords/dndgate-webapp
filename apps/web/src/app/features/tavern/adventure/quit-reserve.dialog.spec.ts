import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { QuitReserveDialogService } from './quit-reserve.dialog';

const characters = [
  { id: 'c1', name: 'Thorin' },
  { id: 'c2', name: 'Bilbo' },
];

async function settle(): Promise<void> {
  TestBed.tick();
  await TestBed.inject(ApplicationRef).whenStable();
}

const dialog = () => document.querySelector<HTMLElement>('dg-quit-reserve-dialog')!;
const boxes = () => [...dialog().querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];
const confirmButton = () =>
  [...dialog().querySelectorAll('button')].find(
    (b) => b.textContent?.trim() === 'Quitter et supprimer',
  )!;
const cancelButton = () =>
  [...dialog().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Annuler')!;
const status = () => dialog().querySelector('[role="status"]')!.textContent?.trim();

async function tick(index: number) {
  boxes()[index].click();
  await settle();
}

describe('QuitReserveDialog', () => {
  function open(missing: number) {
    const results: (string[] | null)[] = [];
    TestBed.inject(QuitReserveDialogService)
      .ask(characters, missing)
      .subscribe((result) => results.push(result));
    return results;
  }

  afterEach(() => document.querySelectorAll('.cdk-overlay-container *').forEach((e) => e.remove()));

  it('lists a labelled checkbox per character, none ticked', async () => {
    open(1);
    await settle();
    expect(boxes()).toHaveLength(2);
    expect(boxes().every((box) => !box.checked)).toBe(true);
    const label = boxes()[0].closest('label')!.textContent;
    expect(label).toContain('Supprimer définitivement « Thorin »');
  });

  it('says how many places are still missing and keeps the button disabled until enough', async () => {
    open(2);
    await settle();
    expect(status()).toBe('Il manque encore 2 places : coche 2 personnages de plus.');
    expect(confirmButton().disabled).toBe(true);
    await tick(0);
    expect(status()).toBe('Il manque encore 1 place : coche 1 personnage de plus.');
    expect(confirmButton().disabled).toBe(true);
    await tick(1);
    expect(status()).toBe('Ta réserve a assez de place.');
    expect(confirmButton().disabled).toBe(false);
  });

  it('unticking a character makes a place missing again', async () => {
    open(1);
    await settle();
    await tick(0);
    expect(confirmButton().disabled).toBe(false);
    await tick(0);
    expect(confirmButton().disabled).toBe(true);
  });

  it('emits the ticked ids on confirm', async () => {
    const results = open(1);
    await settle();
    await tick(1);
    confirmButton().click();
    await settle();
    expect(results).toEqual([['c2']]);
  });

  it('emits null on cancel', async () => {
    const results = open(1);
    await settle();
    cancelButton().click();
    await settle();
    expect(results).toEqual([null]);
  });

  it('does not close when submitted without enough', async () => {
    const results = open(1);
    await settle();
    dialog()
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await settle();
    expect(results).toEqual([]);
    expect(dialog()).not.toBeNull();
  });
});
