import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideApi } from '../../../../core/api/api.providers';
import { CharacterPictureDirective } from './character-picture.directive';
import { stubObjectUrls } from './characters.testing';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CharacterPictureDirective],
  template: `<img [dgCharacterPicture]="id()" #pic="dgCharacterPicture" alt="" />
    <span>{{ pic.status() }}</span>`,
})
class Host {
  readonly id = signal('c1');
}

describe('CharacterPictureDirective', () => {
  let fixture: ComponentFixture<Host>;
  let backend: HttpTestingController;
  let urls: ReturnType<typeof stubObjectUrls>;

  const picture = (id: string) => (r: { url: string }) =>
    new RegExp(`/game-characters/${id}/picture$`).test(r.url);
  const img = () => fixture.nativeElement.querySelector('img') as HTMLImageElement;
  const status = () => fixture.nativeElement.querySelector('span').textContent;

  async function render() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    urls = stubObjectUrls();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    fixture = TestBed.createComponent(Host);
    backend = TestBed.inject(HttpTestingController);
    await render();
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
  });

  it('shows the fetched picture', async () => {
    expect(img().hasAttribute('src')).toBe(false);
    expect(status()).toBe('loading');
    backend.expectOne(picture('c1')).flush(new Blob(['x']));
    await render();
    expect(img().getAttribute('src')).toBe('blob:1');
    expect(status()).toBe('ready');
  });

  it('revokes the url on destroy', async () => {
    backend.expectOne(picture('c1')).flush(new Blob(['x']));
    await render();
    fixture.destroy();
    expect(urls.revoked).toEqual(['blob:1']);
  });

  it('switches picture when the character changes', async () => {
    backend.expectOne(picture('c1')).flush(new Blob(['x']));
    await render();
    fixture.componentInstance.id.set('c2');
    await render();
    expect(urls.revoked).toEqual(['blob:1']);
    expect(img().hasAttribute('src')).toBe(false);
    backend.expectOne(picture('c2')).flush(new Blob(['y']));
    await render();
    expect(img().getAttribute('src')).toBe('blob:2');
  });

  it('reports a failure', async () => {
    backend.expectOne(picture('c1')).flush(new Blob(), { status: 404, statusText: 'Not Found' });
    // The problem is read from the blob body, which takes a few ticks.
    await new Promise((resolve) => setTimeout(resolve));
    await render();
    expect(status()).toBe('failed');
    expect(img().hasAttribute('src')).toBe(false);
  });
});
