import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { provideApi } from './api.providers';
import { ApiConfiguration } from './generated/api-configuration';

describe('provideApi', () => {
  it('sets the API root URL from the environment', () => {
    TestBed.configureTestingModule({ providers: [provideApi()] });

    expect(TestBed.inject(ApiConfiguration).rootUrl).toBe(environment.apiBaseUrl);
  });
});
