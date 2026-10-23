import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { SidebarStateService } from './sidebar-state.service';

describe('SidebarStateService', () => {
  const KEY = 'dg.sidebar.collapsed';
  let small: BehaviorSubject<BreakpointState>;

  function create(isSmall = false): SidebarStateService {
    small = new BehaviorSubject<BreakpointState>({ matches: isSmall, breakpoints: {} });
    TestBed.configureTestingModule({
      providers: [{ provide: BreakpointObserver, useValue: { observe: () => small } }],
    });
    return TestBed.inject(SidebarStateService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('is open by default on a large screen', () => {
    expect(create().collapsed()).toBe(false);
  });

  it('remembers the collapsed state', () => {
    const first = create();
    first.toggle();
    expect(first.collapsed()).toBe(true);
    expect(localStorage.getItem(KEY)).toBe('true');

    TestBed.resetTestingModule();
    expect(create().collapsed()).toBe(true);
  });

  it('works when storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const service = create();
    expect(service.collapsed()).toBe(false);
    expect(() => service.toggle()).not.toThrow();
    expect(service.collapsed()).toBe(true);
  });

  it('starts collapsed on small screens', () => {
    const service = create(true);
    expect(service.collapsed()).toBe(true);
    expect(service.small()).toBe(true);
  });

  it('follows the screen size afterwards', () => {
    const service = create();
    small.next({ matches: true, breakpoints: {} });
    expect(service.collapsed()).toBe(true);
    small.next({ matches: false, breakpoints: {} });
    expect(service.collapsed()).toBe(false);
  });

  it('toggling on a small screen does not overwrite the desktop preference', () => {
    localStorage.setItem(KEY, 'true');
    const service = create(true);
    service.toggle();
    expect(service.collapsed()).toBe(false);
    expect(localStorage.getItem(KEY)).toBe('true');
  });

  it('collapse() closes the drawer without remembering it', () => {
    const service = create();
    service.collapse();
    expect(service.collapsed()).toBe(true);
    expect(localStorage.getItem(KEY)).toBeNull();
  });
});
