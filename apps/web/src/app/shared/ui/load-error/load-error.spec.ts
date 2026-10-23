import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadError } from './load-error';

describe('LoadError', () => {
  let fixture: ComponentFixture<LoadError>;
  let host: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(LoadError);
    host = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('message', 'Impossible de charger le planning.');
  });

  it('announces the message and offers a retry', async () => {
    const retries: string[] = [];
    fixture.componentInstance.retry.subscribe(() => retries.push('retry'));
    await fixture.whenStable();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Impossible de charger le planning.',
    );
    expect(host.querySelector('.load-error__reference')).toBeNull();
    (host.querySelector('button') as HTMLButtonElement).click();
    expect(retries).toEqual(['retry']);
  });

  it('shows the correlation id as selectable text', async () => {
    fixture.componentRef.setInput('problem', { status: 500, errors: [], correlationId: 'cid-7' });
    await fixture.whenStable();

    const reference = host.querySelector('.load-error__reference') as HTMLElement;
    expect(reference.textContent).toContain('Référence :');
    expect(reference.querySelector('code')?.textContent).toBe('cid-7');
  });

  it('shows no reference for a problem without correlation id', async () => {
    fixture.componentRef.setInput('problem', { status: 0, errors: [] });
    await fixture.whenStable();

    expect(host.querySelector('.load-error__reference')).toBeNull();
  });
});
