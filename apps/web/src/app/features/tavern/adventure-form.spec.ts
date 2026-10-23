import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AdventureForm, AdventureValue } from './adventure-form';

describe('AdventureForm', () => {
  const first: AdventureValue = {
    name: 'Premier',
    description: 'd1',
    maxPlayer: 3,
    visibility: 'PUBLIC',
    postalCode: '75001',
    city: 'Paris',
  };

  @Component({
    imports: [AdventureForm],
    template: `<dg-adventure-form [initial]="value()" />`,
  })
  class Host {
    readonly value = signal(first);
  }

  it('applies a new initial value when the input changes after creation', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const name = () => root.querySelector<HTMLInputElement>('[formControlName="name"]')!.value;
    expect(name()).toBe('Premier');

    fixture.componentInstance.value.set({ ...first, name: 'Rechargé', city: 'Lyon', maxPlayer: 5 });
    await fixture.whenStable();
    expect(name()).toBe('Rechargé');
    expect(root.querySelector<HTMLInputElement>('[formControlName="city"]')!.value).toBe('Lyon');
    expect(root.querySelector<HTMLInputElement>('[formControlName="maxPlayer"]')!.value).toBe('5');
  });
});
