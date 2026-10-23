import { TestBed } from '@angular/core/testing';
import { ApiConfiguration } from './core/api/generated/api-configuration';
import { appConfig } from './app.config';
import { routes } from './app.routes';
import { HomePage } from './features/home/home.page';

describe('appConfig', () => {
  it('wires the API client', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(ApiConfiguration).rootUrl).toBe('/dndgate-api');
  });

  it('serves the home page at the root', async () => {
    const home = routes.find((route) => route.path === '');

    expect(home?.pathMatch).toBe('full');
    expect(await home?.loadComponent?.()).toBe(HomePage);
  });
});
