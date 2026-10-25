import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../../core/api/api.providers';
import { CharacterPortrait } from './character-portrait';
import { stubObjectUrls } from './characters.testing';

describe('CharacterPortrait', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    stubObjectUrls();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
  });

  async function render(hasPicture: boolean): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(CharacterPortrait);
    fixture.componentRef.setInput('characterId', 'c1');
    fixture.componentRef.setInput('name', 'Thorin');
    fixture.componentRef.setInput('hasPicture', hasPicture);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('a_character_without_picture_shows_the_default_portrait', async () => {
    const portrait = await render(false);
    backend.expectNone(() => true);
    expect(portrait.querySelector('.placeholder')).not.toBeNull();
    expect(portrait.querySelector('img')?.classList).toContain('hidden');
  });

  it('a character with a picture fetches it and shows it instead of the plate', async () => {
    const portrait = await render(true);
    backend.expectOne((r) => /\/game-characters\/c1\/picture$/.test(r.url)).flush(new Blob(['x']));
    TestBed.tick();
    await Promise.resolve();
    expect(portrait.querySelector('img')?.classList).not.toContain('hidden');
    expect(portrait.querySelector('.placeholder')).toBeNull();
  });
});
