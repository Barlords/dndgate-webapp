import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { ReserveFullDialog } from './reserve-full.dialog';

describe('ReserveFullDialog', () => {
  function ask(confirmed: boolean) {
    const confirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'confirm')
      .mockReturnValue(of(confirmed));
    let answer: string | undefined;
    TestBed.inject(ReserveFullDialog)
      .ask('Thorin')
      .subscribe((value) => (answer = value));
    return { confirm, answer };
  }

  it('names the character and offers the permanent deletion', () => {
    const { confirm, answer } = ask(true);
    expect(answer).toBe('delete');
    const options = confirm.mock.calls[0][0];
    expect(options.confirmLabel).toBe('Supprimer définitivement');
    expect(options.message).toContain('Thorin');
  });

  it('answers cancel when the reader refuses', () => {
    expect(ask(false).answer).toBe('cancel');
  });
});
