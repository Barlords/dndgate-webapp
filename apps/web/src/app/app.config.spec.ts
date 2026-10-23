import { TestBed } from '@angular/core/testing';
import { ApiConfiguration } from './core/api/generated/api-configuration';
import { appConfig } from './app.config';
import { routes } from './app.routes';

describe('appConfig', () => {
  it('wires the API client and an initial routing table', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(ApiConfiguration).rootUrl).toBe('/dndgate-api');
    expect(routes).toEqual([]);
  });
});
