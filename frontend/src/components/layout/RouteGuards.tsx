import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/lib/auth";

function FullPageSpinner() {
  return (
    <div role="status" aria-label="Loading" className="grid min-h-dvh place-items-center">
      <div className="size-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
    </div>
  );
}

/** Signed-in users only; sends new users to onboarding first. */
export function RequireAuth({ allowOnboarding = false }: { allowOnboarding?: boolean }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === "loading") return <FullPageSpinner />;
  if (status === "anonymous") return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (!allowOnboarding && user && !user.onboardingCompleted) return <Navigate to="/onboarding" replace />;
  return <Outlet />;
}

/** Login/register: signed-in users go straight to the app. */
export function RedirectIfAuthed() {
  const { status } = useAuth();
  if (status === "loading") return <FullPageSpinner />;
  if (status === "authenticated") return <Navigate to="/dashboard" replace />;
  return <Outlet />;
}
