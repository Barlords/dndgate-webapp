import { IconName } from '../shared/ui/icon/icon-paths';

export interface NavItem {
  label: string;
  icon: IconName;
  link: string;
  /** Active only on this exact URL (a section's index page). */
  exact?: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}
