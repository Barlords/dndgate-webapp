import { TestBed } from '@angular/core/testing';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ToastOutlet } from './toast-outlet';

describe('ToastOutlet', () => {
  function render() {
    const fixture = TestBed.createComponent(ToastOutlet);
    const notifier = TestBed.inject(NotifierService);
    return { fixture, notifier, el: fixture.nativeElement as HTMLElement };
  }

  it('is a polite live region', async () => {
    const { fixture, el } = render();
    await fixture.whenStable();
    expect(el.querySelector('[aria-live="polite"]')).not.toBeNull();
  });

  it('shows the correlation id as selectable text after "Référence :"', async () => {
    const { fixture, notifier, el } = render();
    notifier.error({ status: 500, errors: [], correlationId: 'cid-42', detail: 'Boom' });
    await fixture.whenStable();
    const reference = el.querySelector('.dg-toast__reference') as HTMLElement;
    expect(reference.textContent).toContain('Référence :');
    expect(reference.querySelector('code')?.textContent).toBe('cid-42');
    expect(el.querySelector('.dg-toast--error')).not.toBeNull();
    expect(el.textContent).toContain('Boom');
  });

  it('shows no reference when the problem has none', async () => {
    const { fixture, notifier, el } = render();
    notifier.success('Enregistré.');
    await fixture.whenStable();
    expect(el.querySelector('.dg-toast--error')).toBeNull();
    expect(el.querySelector('.dg-toast__reference')).toBeNull();
    expect(el.textContent).toContain('Enregistré.');
  });

  it('closes a toast with its close button', async () => {
    const { fixture, notifier, el } = render();
    notifier.success('Ok');
    await fixture.whenStable();
    (el.querySelector('.dg-toast__close') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('.dg-toast')).toBeNull();
    expect(notifier.toasts()).toHaveLength(0);
  });

  it('reloads from the conflict toast and closes it', async () => {
    const { fixture, notifier, el } = render();
    const reload = vi.fn();
    notifier.conflict(reload);
    await fixture.whenStable();
    const button = el.querySelector('dg-button button') as HTMLButtonElement;
    expect(button.textContent).toContain('Recharger');
    button.click();
    await fixture.whenStable();
    expect(reload).toHaveBeenCalledOnce();
    expect(el.querySelector('.dg-toast')).toBeNull();
  });

  it('pauses the auto-close while the toast is hovered or focused', async () => {
    vi.useFakeTimers();
    try {
      const { fixture, notifier, el } = render();
      notifier.success('Ok');
      fixture.detectChanges();
      const toast = el.querySelector('.dg-toast') as HTMLElement;
      toast.dispatchEvent(new Event('mouseenter'));
      vi.advanceTimersByTime(30_000);
      expect(notifier.toasts()).toHaveLength(1);
      toast.dispatchEvent(new Event('mouseleave'));
      toast.dispatchEvent(new Event('focusin'));
      vi.advanceTimersByTime(30_000);
      expect(notifier.toasts()).toHaveLength(1);
      toast.dispatchEvent(new Event('focusout'));
      vi.advanceTimersByTime(6001);
      expect(notifier.toasts()).toHaveLength(0);
    } finally {
      vi.useRealTimers();
    }
  });
});
