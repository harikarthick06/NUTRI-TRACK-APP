import {
  BarChart3,
  BookOpen,
  CalendarDays,
  ChefHat,
  Droplets,
  LayoutDashboard,
  Pill,
  Scale,
  Search,
  Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  to: string;
  label: string;
  /** Label for the mobile bottom bar, where space is tight. */
  short?: string;
  icon: LucideIcon;
}

export const NAV_ITEMS: NavItem[] = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/diary", label: "Food diary", short: "Diary", icon: BookOpen },
  { to: "/foods", label: "Food search", short: "Search", icon: Search },
  { to: "/recipes", label: "Recipes", icon: ChefHat },
  { to: "/nutrients", label: "Vitamins & minerals", short: "Vitamins", icon: Pill },
  { to: "/weight", label: "Weight", icon: Scale },
  { to: "/water", label: "Water", icon: Droplets },
  { to: "/reports", label: "Reports", icon: BarChart3 },
  { to: "/calendar", label: "Calendar", icon: CalendarDays },
  { to: "/settings", label: "Profile & settings", icon: Settings },
];

/** Shown in the mobile bottom bar; the rest live in the "More" sheet. */
export const MOBILE_PRIMARY = ["/dashboard", "/diary", "/foods", "/nutrients"];
