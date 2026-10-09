import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { RedirectIfAuthed, RequireAuth } from "@/components/layout/RouteGuards";
import { PageSkeleton } from "@/components/ui/skeleton";
import LandingPage from "@/pages/LandingPage";
import LoginPage from "@/pages/LoginPage";
import RegisterPage from "@/pages/RegisterPage";

const OnboardingPage = lazy(() => import("@/pages/OnboardingPage"));
const DashboardPage = lazy(() => import("@/pages/DashboardPage"));
const DiaryPage = lazy(() => import("@/pages/DiaryPage"));
const FoodSearchPage = lazy(() => import("@/pages/FoodSearchPage"));
const FoodDetailPage = lazy(() => import("@/pages/FoodDetailPage"));
const FoodFormPage = lazy(() => import("@/pages/FoodFormPage"));
const RecipesPage = lazy(() => import("@/pages/RecipesPage"));
const RecipeBuilderPage = lazy(() => import("@/pages/RecipeBuilderPage"));
const NutrientsPage = lazy(() => import("@/pages/NutrientsPage"));
const WeightPage = lazy(() => import("@/pages/WeightPage"));
const WaterPage = lazy(() => import("@/pages/WaterPage"));
const ReportsPage = lazy(() => import("@/pages/ReportsPage"));
const CalendarPage = lazy(() => import("@/pages/CalendarPage"));
const SettingsPage = lazy(() => import("@/pages/SettingsPage"));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"));

export default function App() {
  return (
    <Suspense fallback={<div className="p-6"><PageSkeleton /></div>}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route element={<RedirectIfAuthed />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
        <Route element={<RequireAuth allowOnboarding />}>
          <Route path="/onboarding" element={<OnboardingPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/diary" element={<DiaryPage />} />
            <Route path="/foods" element={<FoodSearchPage />} />
            <Route path="/foods/new" element={<FoodFormPage />} />
            <Route path="/foods/:id" element={<FoodDetailPage />} />
            <Route path="/foods/:id/edit" element={<FoodFormPage />} />
            <Route path="/recipes" element={<RecipesPage />} />
            <Route path="/recipes/new" element={<RecipeBuilderPage />} />
            <Route path="/recipes/:id" element={<RecipeBuilderPage />} />
            <Route path="/nutrients" element={<NutrientsPage />} />
            <Route path="/weight" element={<WeightPage />} />
            <Route path="/water" element={<WaterPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/calendar" element={<CalendarPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/profile" element={<Navigate to="/settings" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
