import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/lib/auth";
import { ApiError, errorMessage } from "@/lib/api";
import { AuthCard } from "./AuthCard";

export const passwordRule = z
  .string()
  .min(8, "At least 8 characters")
  .max(128, "At most 128 characters")
  .regex(/[A-Za-z]/, "Include a letter")
  .regex(/\d/, "Include a number");

const schema = z
  .object({
    name: z.string().trim().min(1, "Enter your name").max(100),
    email: z.string().trim().email("Enter a valid email"),
    password: passwordRule,
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { path: ["confirm"], message: "Passwords don't match" });
type Values = z.infer<typeof schema>;

export default function RegisterPage() {
  const { register: signUp } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, formState, setError } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = async (v: Values) => {
    setFormError(null);
    try {
      await signUp(v.name, v.email, v.password);
      navigate("/onboarding", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field === "email" || field === "password" || field === "name") setError(field, { message });
        }
      } else {
        setFormError(errorMessage(err));
      }
    }
  };

  return (
    <AuthCard
      title="Create your account"
      description="Start tracking your meals and nutrients."
      footer={
        <>
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4" noValidate>
        {formError && (
          <p role="alert" className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
            {formError}
          </p>
        )}
        <Field label="Name" htmlFor="name" error={formState.errors.name?.message}>
          <Input id="name" autoComplete="name" aria-invalid={!!formState.errors.name} {...register("name")} />
        </Field>
        <Field label="Email" htmlFor="email" error={formState.errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" aria-invalid={!!formState.errors.email} {...register("email")} />
        </Field>
        <Field label="Password" htmlFor="password" error={formState.errors.password?.message} hint="At least 8 characters, with a letter and a number">
          <Input id="password" type="password" autoComplete="new-password" aria-invalid={!!formState.errors.password} {...register("password")} />
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={formState.errors.confirm?.message}>
          <Input id="confirm" type="password" autoComplete="new-password" aria-invalid={!!formState.errors.confirm} {...register("confirm")} />
        </Field>
        <Button type="submit" disabled={formState.isSubmitting}>
          {formState.isSubmitting ? "Creating account…" : "Create account"}
        </Button>
      </form>
    </AuthCard>
  );
}
