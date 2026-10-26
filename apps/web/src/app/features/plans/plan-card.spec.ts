import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlanDto } from '../../core/api/generated/models/plan-dto';
import { BillingInterval, PlanAction, PlanCard } from './plan-card';

describe('PlanCard', () => {
  let fixture: ComponentFixture<PlanCard>;

  async function render(plan: PlanDto, interval: BillingInterval, action: PlanAction = 'choose') {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(PlanCard);
    fixture.componentRef.setInput('plan', plan);
    fixture.componentRef.setInput('currency', 'EUR');
    fixture.componentRef.setInput('interval', interval);
    fixture.componentRef.setInput('action', action);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (element: Element | null) =>
    (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const MONTHLY_ONLY: PlanDto = { id: 'hero', prices: { monthly: { amount: 99 } } };

  it('a_free_plan_says_free', async () => {
    const root = await render({ id: 'adventurer', prices: {} }, 'monthly', 'current');
    expect(text(root.querySelector('.card__price'))).toBe('Gratuit');
  });

  it('a_paid_plan_without_a_price_for_the_period_is_unavailable_and_has_no_action', async () => {
    const root = await render(MONTHLY_ONLY, 'yearly');
    expect(text(root.querySelector('.card__price'))).toBe('Indisponible en annuel');
    expect(text(root)).not.toContain('Gratuit');
    expect(text(root)).not.toContain('2 mois offerts');
    expect(root.querySelector('.card__action button, .card__action a')).toBeNull();
  });

  it('the_missing_period_is_named_monthly_too', async () => {
    const root = await render({ id: 'hero', prices: { yearly: { amount: 990 } } }, 'monthly');
    expect(text(root.querySelector('.card__price'))).toBe('Indisponible en mensuel');
    expect(root.querySelector('.card__action button')).toBeNull();
  });

  it('the_period_that_has_a_price_keeps_its_price_and_action', async () => {
    const root = await render(MONTHLY_ONLY, 'monthly');
    expect(text(root.querySelector('.card__price'))).toContain('0,99');
    expect(text(root.querySelector('.card__action button'))).toBe('Choisir');
  });
});
