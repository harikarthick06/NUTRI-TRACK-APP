import { Link } from "react-router-dom";
import { BarChart3, BookOpen, ChefHat, Droplets, Pill, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Logo, ThemeToggle } from "@/components/layout/AppLayout";
import { useAuth } from "@/lib/auth";

const FEATURES = [
  { icon: BookOpen, title: "Log every meal", text: "Six meals a day, portions like '2 idli' or '1 katori sambar', and raw or cooked weights." },
  { icon: ChefHat, title: "Indian recipes built in", text: "Idli, dosa, sambar, rasam, curd rice, biryani, vanjaram fry and more, or build your own." },
  { icon: Pill, title: "Full micronutrients", text: "All 13 vitamins, 15 minerals, omega-3 and omega-6, with how complete the data is." },
  { icon: BarChart3, title: "Trends and reports", text: "Weekly and monthly averages, calendar history and CSV exports." },
  { icon: Droplets, title: "Water and weight", text: "Quick-add water, weight trends and goal progress." },
  { icon: ShieldCheck, title: "Honest data", text: "Every food shows its source. Missing values stay unknown, never zero." },
];

export default function LandingPage() {
  const { status } = useAuth();
  const authed = status === "authenticated";
  return (
    <div className="min-h-dvh bg-background">
      <header className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Logo />
        <div className="flex items-center gap-2">
          <ThemeToggle />
          {authed ? (
            <Button asChild>
              <Link to="/dashboard">Open app</Link>
            </Button>
          ) : (
            <>
              <Button variant="ghost" asChild>
                <Link to="/login">Log in</Link>
              </Button>
              <Button asChild>
                <Link to="/register">Sign up</Link>
              </Button>
            </>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 pb-16 sm:px-6">
        <section className="py-14 text-center sm:py-20">
          <h1 className="mx-auto max-w-3xl text-4xl font-semibold tracking-tight sm:text-5xl">
            Know what's on your plate, from <span className="text-primary">idli to biryani</span>.
          </h1>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-muted-foreground">
            NutriTrack logs your meals and adds up calories, macros, vitamins and minerals, using Indian (IFCT 2017) and
            USDA food composition data.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button size="lg" asChild>
              <Link to={authed ? "/dashboard" : "/register"}>{authed ? "Go to dashboard" : "Create a free account"}</Link>
            </Button>
            {!authed && (
              <Button size="lg" variant="outline" asChild>
                <Link to="/login">I already have an account</Link>
              </Button>
            )}
          </div>
        </section>
        <section aria-label="Features" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <Card key={title}>
              <CardContent className="flex gap-4 pt-5">
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="size-5" aria-hidden />
                </span>
                <div>
                  <h2 className="font-semibold">{title}</h2>
                  <p className="mt-1 text-sm text-muted-foreground">{text}</p>
                </div>
              </CardContent>
            </Card>
          ))}
        </section>
        <p className="mx-auto mt-12 max-w-2xl text-center text-xs text-muted-foreground">
          NutriTrack is a tracking tool, not medical advice. It doesn't diagnose deficiencies or recommend supplements. Talk to a
          doctor or registered dietitian about your own needs.
        </p>
      </main>
    </div>
  );
}
