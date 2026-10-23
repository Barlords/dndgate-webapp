import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { provideApi } from './core/api/api.providers';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { Dnd5e2014Ruleset } from './features/library/rulesets/dnd5e/dnd5e-2014.ruleset';
import { Dnd5e2024Ruleset } from './features/library/rulesets/dnd5e/dnd5e-2024.ruleset';
import { RULESETS } from './features/library/rulesets/ruleset.model';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideApi(),
    { provide: RULESETS, useExisting: Dnd5e2014Ruleset, multi: true },
    { provide: RULESETS, useExisting: Dnd5e2024Ruleset, multi: true },
    provideAppInitializer(() => inject(AuthService).restore()),
  ],
};
