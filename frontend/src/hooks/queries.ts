import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type {
  CalendarMonth,
  CustomFoodRequest,
  DailyNutrition,
  DiaryDay,
  DiaryEntry,
  EntryRequest,
  ExternalFoodSummary,
  FoodDetail,
  FoodSummary,
  MicronutrientView,
  NutrientDef,
  PageResponse,
  Profile,
  ProfileUpdate,
  ProviderStatus,
  RangeReport,
  RecipeDetail,
  RecipeNutrition,
  RecipeRequest,
  RecipeSummary,
  ReferenceRow,
  SavedMeal,
  TargetSuggestion,
  TargetsResponse,
  Trend,
  WaterDay,
  WaterHistory,
  WeightEntry,
  WeightHistory,
  WeightRequest,
  MealType,
  IngredientInput,
} from "@/lib/types";

/** Everything derived from the diary; invalidated together after any diary change. */
const DIARY_DERIVED = ["diary", "nutrition", "micro", "report", "calendar", "trend"] as const;

export function useInvalidateDiary() {
  const qc = useQueryClient();
  return () => {
    for (const key of DIARY_DERIVED) qc.invalidateQueries({ queryKey: [key] });
    qc.invalidateQueries({ queryKey: ["foods", "recent"] });
  };
}

// ---------------------------------------------------------------- profile & targets

export const useProfile = () => useQuery({ queryKey: ["profile"], queryFn: () => api<Profile>("/api/profile") });

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProfileUpdate) => api<Profile>("/api/profile", { method: "PUT", body }),
    onSuccess: (p) => {
      qc.setQueryData(["profile"], p);
      for (const key of ["targets", "nutrition", "micro", "report", "calendar", "water", "trend", "weight"]) {
        qc.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}

export const useTargetSuggestions = (enabled: boolean) =>
  useQuery({
    queryKey: ["profile", "suggestions"],
    queryFn: () => api<TargetSuggestion>("/api/profile/target-suggestions"),
    enabled,
    retry: false,
  });

export const useTargets = () =>
  useQuery({ queryKey: ["targets"], queryFn: () => api<TargetsResponse>("/api/nutrient-targets") });

export function useUpdateTargets() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targets: { code: string; target: number | null; upperLimit: number | null }[]) =>
      api<TargetsResponse>("/api/nutrient-targets", { method: "PUT", body: { targets } }),
    onSuccess: (t) => {
      qc.setQueryData(["targets"], t);
      for (const key of ["nutrition", "micro", "report", "trend"]) qc.invalidateQueries({ queryKey: [key] });
    },
  });
}

export function useResetTarget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (code: string | null) =>
      api<TargetsResponse>(code ? `/api/nutrient-targets/${code}` : "/api/nutrient-targets", { method: "DELETE" }),
    onSuccess: (t) => {
      qc.setQueryData(["targets"], t);
      for (const key of ["nutrition", "micro", "report", "trend"]) qc.invalidateQueries({ queryKey: [key] });
    },
  });
}

export const useReferenceValues = (code?: string) =>
  useQuery({
    queryKey: ["references", code ?? "all"],
    queryFn: () =>
      api<{ systems: string[]; disclaimer: string; values: ReferenceRow[] }>("/api/reference-values", {
        query: { code },
      }),
  });

export const useNutrients = () =>
  useQuery({ queryKey: ["nutrients"], queryFn: () => api<NutrientDef[]>("/api/nutrients"), staleTime: Infinity });

// ---------------------------------------------------------------- foods

export type FoodScope = "ALL" | "MINE" | "FAVORITES" | "RECENT";

export const useFoodSearch = (q: string, scope: FoodScope, category: string, page: number) =>
  useQuery({
    queryKey: ["foods", "search", q, scope, category, page],
    queryFn: () =>
      api<PageResponse<FoodSummary>>("/api/foods/search", { query: { q, scope, category, page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

export const useRecentFoods = () =>
  useQuery({
    queryKey: ["foods", "recent"],
    queryFn: () => api<PageResponse<FoodSummary>>("/api/foods/recent", { query: { size: 12 } }),
  });

export const useFoodCategories = () =>
  useQuery({ queryKey: ["foods", "categories"], queryFn: () => api<string[]>("/api/foods/categories") });

export const useFood = (id: number | null | undefined) =>
  useQuery({
    queryKey: ["foods", "detail", id],
    queryFn: () => api<FoodDetail>(`/api/foods/${id}`),
    enabled: !!id,
  });

export function useToggleFavorite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, favorite }: { id: number; favorite: boolean }) =>
      api<void>(`/api/foods/${id}/favorite`, { method: favorite ? "POST" : "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["foods"] }),
  });
}

export function useSaveCustomFood(id?: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CustomFoodRequest) =>
      api<FoodDetail>(id ? `/api/foods/${id}` : "/api/foods/custom", { method: id ? "PUT" : "POST", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["foods"] }),
  });
}

export function useArchiveFood() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/foods/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["foods"] }),
  });
}

export const useProviders = () =>
  useQuery({ queryKey: ["foods", "providers"], queryFn: () => api<ProviderStatus[]>("/api/foods/providers") });

export const useExternalSearch = (provider: string, q: string) =>
  useQuery({
    queryKey: ["foods", "external", provider, q],
    queryFn: () => api<ExternalFoodSummary[]>("/api/foods/external/search", { query: { provider, q, limit: 20 } }),
    enabled: !!provider && q.trim().length >= 2,
    retry: false,
  });

export function useImportFood() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { provider: string; externalId: string }) =>
      api<FoodDetail>("/api/foods/import", { method: "POST", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["foods"] }),
  });
}

// ---------------------------------------------------------------- diary

export const useDiary = (date: string) =>
  useQuery({ queryKey: ["diary", date], queryFn: () => api<DiaryDay>("/api/diary", { query: { date } }) });

export function useAddEntry() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: (body: EntryRequest) => api<DiaryEntry>("/api/diary/entries", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

export function useUpdateEntry() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: Omit<EntryRequest, "foodId"> }) =>
      api<DiaryEntry>(`/api/diary/entries/${id}`, { method: "PUT", body }),
    onSuccess: invalidate,
  });
}

export function useDeleteEntry() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/diary/entries/${id}`, { method: "DELETE" }),
    onSuccess: invalidate,
  });
}

export function useCopyDay() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: (body: { fromDate: string; toDate: string; mealTypes?: MealType[] }) =>
      api<{ copiedEntries: number; toDate: string }>("/api/diary/copy-day", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

export function useCopyMeal() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: (body: { fromDate: string; fromMeal: MealType; toDate: string; toMeal: MealType }) =>
      api<{ copiedEntries: number; toDate: string }>("/api/diary/copy-meal", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

export function useSetDayStatus() {
  const invalidate = useInvalidateDiary();
  return useMutation({
    mutationFn: ({ date, completed, note }: { date: string; completed: boolean; note?: string }) =>
      api<void>(`/api/diary/days/${date}`, { method: "PUT", body: { completed, note } }),
    onSuccess: invalidate,
  });
}

// ---------------------------------------------------------------- nutrition

export const useDailyNutrition = (date: string) =>
  useQuery({
    queryKey: ["nutrition", "daily", date],
    queryFn: () => api<DailyNutrition>("/api/nutrition/daily", { query: { date } }),
  });

export const useMicronutrients = (date: string) =>
  useQuery({
    queryKey: ["micro", date],
    queryFn: () => api<MicronutrientView>("/api/nutrition/micronutrients", { query: { date } }),
  });

// ---------------------------------------------------------------- recipes & saved meals

export const useRecipes = () =>
  useQuery({ queryKey: ["recipes"], queryFn: () => api<RecipeSummary[]>("/api/recipes") });

export const useRecipe = (id: number | null) =>
  useQuery({ queryKey: ["recipes", id], queryFn: () => api<RecipeDetail>(`/api/recipes/${id}`), enabled: !!id });

export function useRecipePreview(body: RecipeRequest | null) {
  return useQuery({
    queryKey: ["recipes", "preview", body],
    queryFn: () => api<RecipeNutrition>("/api/recipes/calculate", { method: "POST", body }),
    enabled: !!body && body.ingredients.length > 0 && body.servings > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useSaveRecipe(id?: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RecipeRequest) =>
      api<RecipeDetail>(id ? `/api/recipes/${id}` : "/api/recipes", { method: id ? "PUT" : "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["recipes"] });
      qc.invalidateQueries({ queryKey: ["foods"] });
    },
  });
}

export function useRecipeAction() {
  const qc = useQueryClient();
  const invalidate = useInvalidateDiary();
  return {
    duplicate: useMutation({
      mutationFn: (id: number) => api<RecipeDetail>(`/api/recipes/${id}/duplicate`, { method: "POST" }),
      onSuccess: () => qc.invalidateQueries({ queryKey: ["recipes"] }),
    }),
    remove: useMutation({
      mutationFn: (id: number) => api<void>(`/api/recipes/${id}`, { method: "DELETE" }),
      onSuccess: () => {
        qc.invalidateQueries({ queryKey: ["recipes"] });
        qc.invalidateQueries({ queryKey: ["foods"] });
      },
    }),
    log: useMutation({
      mutationFn: ({ id, ...body }: { id: number; date: string; mealType: MealType; servings: number; wholeRecipe?: boolean }) =>
        api<DiaryEntry>(`/api/recipes/${id}/log`, { method: "POST", body }),
      onSuccess: invalidate,
    }),
  };
}

export const useSavedMeals = () =>
  useQuery({ queryKey: ["savedMeals"], queryFn: () => api<SavedMeal[]>("/api/saved-meals") });

export function useSavedMealActions() {
  const qc = useQueryClient();
  const invalidate = useInvalidateDiary();
  const refresh = () => qc.invalidateQueries({ queryKey: ["savedMeals"] });
  return {
    fromDiary: useMutation({
      mutationFn: (body: { name: string; date: string; mealType: MealType }) =>
        api<SavedMeal>("/api/saved-meals/from-diary", { method: "POST", body }),
      onSuccess: refresh,
    }),
    create: useMutation({
      mutationFn: (body: { name: string; mealType?: MealType; notes?: string; items: IngredientInput[] }) =>
        api<SavedMeal>("/api/saved-meals", { method: "POST", body }),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: number) => api<void>(`/api/saved-meals/${id}`, { method: "DELETE" }),
      onSuccess: refresh,
    }),
    log: useMutation({
      mutationFn: ({ id, date, mealType }: { id: number; date: string; mealType: MealType }) =>
        api<{ loggedEntries: number }>(`/api/saved-meals/${id}/log`, { method: "POST", body: { date, mealType } }),
      onSuccess: invalidate,
    }),
  };
}

// ---------------------------------------------------------------- weight & water

export const useWeightHistory = (from: string, to: string) =>
  useQuery({
    queryKey: ["weight", from, to],
    queryFn: () => api<WeightHistory>("/api/weight-logs", { query: { from, to } }),
  });

export function useWeightActions() {
  const qc = useQueryClient();
  const refresh = () => {
    for (const key of ["weight", "profile", "report", "calendar"]) qc.invalidateQueries({ queryKey: [key] });
  };
  return {
    add: useMutation({
      mutationFn: (body: WeightRequest) => api<WeightEntry>("/api/weight-logs", { method: "POST", body }),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: number; body: WeightRequest }) =>
        api<WeightEntry>(`/api/weight-logs/${id}`, { method: "PUT", body }),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: number) => api<void>(`/api/weight-logs/${id}`, { method: "DELETE" }),
      onSuccess: refresh,
    }),
  };
}

export const useWaterDay = (date: string) =>
  useQuery({ queryKey: ["water", date], queryFn: () => api<WaterDay>("/api/water-logs", { query: { date } }) });

export const useWaterHistory = (from: string, to: string) =>
  useQuery({
    queryKey: ["water", "history", from, to],
    queryFn: () => api<WaterHistory>("/api/water-logs/history", { query: { from, to } }),
  });

export function useWaterActions() {
  const qc = useQueryClient();
  const refresh = () => {
    for (const key of ["water", "report", "calendar"]) qc.invalidateQueries({ queryKey: [key] });
  };
  return {
    add: useMutation({
      mutationFn: (body: { date: string; amountMl: number; note?: string }) =>
        api<WaterDay>("/api/water-logs", { method: "POST", body }),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: ({ id, amountMl, note }: { id: number; amountMl: number; note?: string }) =>
        api<WaterDay>(`/api/water-logs/${id}`, { method: "PUT", body: { amountMl, note } }),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: number) => api<WaterDay>(`/api/water-logs/${id}`, { method: "DELETE" }),
      onSuccess: refresh,
    }),
  };
}

// ---------------------------------------------------------------- reports

export type ReportParams =
  | { kind: "weekly"; date: string }
  | { kind: "monthly"; month: string }
  | { kind: "custom"; from: string; to: string };

export const useReport = (params: ReportParams) =>
  useQuery({
    queryKey: ["report", params],
    queryFn: () => {
      if (params.kind === "weekly") return api<RangeReport>("/api/reports/weekly", { query: { date: params.date } });
      if (params.kind === "monthly") return api<RangeReport>("/api/reports/monthly", { query: { month: params.month } });
      return api<RangeReport>("/api/reports/custom", { query: { from: params.from, to: params.to } });
    },
    placeholderData: keepPreviousData,
  });

export const useCalendar = (month: string) =>
  useQuery({ queryKey: ["calendar", month], queryFn: () => api<CalendarMonth>("/api/calendar", { query: { month } }) });

export const useTrend = (code: string, from: string, to: string) =>
  useQuery({
    queryKey: ["trend", code, from, to],
    queryFn: () => api<Trend>("/api/analytics/trend", { query: { code, from, to } }),
    placeholderData: keepPreviousData,
  });
