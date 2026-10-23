import { vi } from 'vitest';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';

/** For tests only: a character as the API returns it. */
export function character(overrides: Partial<GameCharacterDto> = {}): GameCharacterDto {
  return {
    id: 'c1',
    name: 'Thorin',
    race: 'Nain',
    martialClass: 'Guerrier',
    personality: 'Têtu.',
    background: 'Roi sous la montagne.',
    pictureUrl: '/game-characters/c1/picture',
    ownerId: 'p1',
    ...overrides,
  };
}

/** For tests only: replaces `URL.createObjectURL` / `revokeObjectURL` and records their calls. */
export function stubObjectUrls(): { created: Blob[]; revoked: string[] } {
  const created: Blob[] = [];
  const revoked: string[] = [];
  vi.stubGlobal(
    'URL',
    Object.assign(URL, {
      createObjectURL: (blob: Blob) => {
        created.push(blob);
        return `blob:${created.length}`;
      },
      revokeObjectURL: (url: string) => revoked.push(url),
    }),
  );
  return { created, revoked };
}

export const PNG_BYTES = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
export const JPEG_BYTES = [0xff, 0xd8, 0xff, 0xe0];
export const GIF_BYTES = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61];

/** For tests only: a file starting with `bytes`, padded to `size` bytes. */
export function fileOf(bytes: number[], name: string, size = bytes.length): File {
  const content = new Uint8Array(Math.max(size, bytes.length));
  content.set(bytes);
  return new File([content], name);
}
