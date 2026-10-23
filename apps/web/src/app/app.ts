import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastOutlet } from './shared/ui/toast-outlet/toast-outlet';

@Component({
  imports: [RouterOutlet, ToastOutlet],
  selector: 'dg-root',
  template: `
    <router-outlet />
    <dg-toast-outlet />
  `,
})
export class App {}
