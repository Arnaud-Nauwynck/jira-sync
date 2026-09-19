import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  importProvidersFrom,
} from '@angular/core';
import { RouteReuseStrategy, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { provideApi } from './rest/provide-api';
import { SearchPageRouteReuseStrategy } from './utils/search-page-route-reuse.strategy';
import { provideHighcharts } from 'highcharts-angular';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    importProvidersFrom(NgbModule),
    provideApi(''),
    provideHighcharts(),
    { provide: RouteReuseStrategy, useClass: SearchPageRouteReuseStrategy },
  ],
};
