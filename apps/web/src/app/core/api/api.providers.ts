import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { environment } from '../../../environments/environment';
import { provideApiConfiguration } from './generated/api-configuration';

/** Points the generated API client at the configured base URL. */
export function provideApi(): EnvironmentProviders {
  return makeEnvironmentProviders([provideApiConfiguration(environment.apiBaseUrl)]);
}
