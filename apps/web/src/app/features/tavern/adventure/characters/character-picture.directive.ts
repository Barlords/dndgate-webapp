import { Directive, effect, ElementRef, inject, input, signal } from '@angular/core';
import { CharacterPictureService } from './character-picture.service';

export type PictureStatus = 'loading' | 'ready' | 'failed';

/**
 * `<img [dgCharacterPicture]="characterId">`: shows the character's picture, fetched with the
 * access token. The object URL is released when the image goes away or changes character.
 */
@Directive({
  selector: 'img[dgCharacterPicture]',
  exportAs: 'dgCharacterPicture',
})
export class CharacterPictureDirective {
  readonly dgCharacterPicture = input.required<string>();

  private readonly pictures = inject(CharacterPictureService);
  private readonly image = inject<ElementRef<HTMLImageElement>>(ElementRef).nativeElement;
  private readonly state = signal<PictureStatus>('loading');

  readonly status = this.state.asReadonly();

  constructor() {
    effect((onCleanup) => {
      const id = this.dgCharacterPicture();
      this.image.removeAttribute('src');
      this.state.set('loading');
      const subscription = this.pictures.objectUrl(id).subscribe({
        next: (url) => {
          // Set directly: Angular's URL sanitizer would turn a `blob:` address into `unsafe:`.
          this.image.setAttribute('src', url);
          this.state.set('ready');
        },
        error: () => this.state.set('failed'),
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
