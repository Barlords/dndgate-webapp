import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { FakeRuleset } from './fake.ruleset';
import { RulesetRegistry } from './ruleset-registry';
import { RULESETS } from './ruleset.model';
import { SelectedRulesetService } from './selected-ruleset.service';

@Component({ template: '' })
class Stub {}

describe('SelectedRulesetService', () => {
  let router: Router;

  function setup(stored: string | null = null): SelectedRulesetService {
    localStorage.clear();
    if (stored !== null) {
      localStorage.setItem('dg.ruleset', stored);
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'library/grimoires', component: Stub },
          { path: 'library/:rulesetId', component: Stub },
          { path: 'library/:rulesetId/:sectionId', component: Stub },
        ]),
        { provide: RULESETS, multi: true, useValue: new FakeRuleset({ id: 'other-game' }) },
        { provide: RULESETS, multi: true, useValue: new FakeRuleset({ id: 'dnd5e-2014' }) },
      ],
    });
    router = TestBed.inject(Router);
    return TestBed.inject(SelectedRulesetService);
  }

  it('selected ruleset follows the url and falls back to dnd5e-2014', async () => {
    const selected = setup();
    expect(selected.current().id).toBe('dnd5e-2014');

    await router.navigateByUrl('/library/other-game/spells');
    expect(selected.current().id).toBe('other-game');
    expect(selected.sectionId()).toBe('spells');

    await router.navigateByUrl('/library/dnd5e-2014');
    expect(selected.current().id).toBe('dnd5e-2014');
    expect(selected.sectionId()).toBeNull();
  });

  it('remembers the last ruleset of the url under dg.ruleset', async () => {
    const selected = setup();
    await router.navigateByUrl('/library/other-game');
    expect(localStorage.getItem('dg.ruleset')).toBe('other-game');

    await router.navigateByUrl('/library/grimoires');
    expect(selected.current().id).toBe('other-game');
  });

  it('starts from the remembered ruleset, unless it is unknown', () => {
    expect(setup('other-game').current().id).toBe('other-game');
    TestBed.resetTestingModule();
    expect(setup('gone-game').current().id).toBe('dnd5e-2014');
  });

  it('ignores an unknown ruleset in the url', async () => {
    const selected = setup('other-game');
    await router.navigateByUrl('/library/gone-game');
    expect(selected.current().id).toBe('other-game');
    expect(localStorage.getItem('dg.ruleset')).toBe('other-game');
  });

  it('registry lists and finds the registered rulesets', () => {
    setup();
    const registry = TestBed.inject(RulesetRegistry);
    expect(registry.all().map((ruleset) => ruleset.id)).toEqual(['other-game', 'dnd5e-2014']);
    expect(registry.get('other-game')?.id).toBe('other-game');
    expect(registry.get('gone-game')).toBeUndefined();
  });
});
