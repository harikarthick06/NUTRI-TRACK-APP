// Types mirror the backend DTOs. Optional fields are omitted by the API when null.

export type MealType = "BREAKFAST" | "MORNING_SNACK" | "LUNCH" | "EVENING_SNACK" | "DINNER" | "OTHER";
export const MEAL_TYPES: MealType[] = ["BREAKFAST", "MORNING_SNACK", "LUNCH", "EVENING_SNACK", "DINNER", "OTHER"];
export const MEAL_LABELS: Record<MealType, string> = {
  BREAKFAST: "Breakfast",
  MORNING_SNACK: "Morning snack",
  LUNCH: "Lunch",
  EVENING_SNACK: "Evening snack",
  DINNER: "Dinner",
  OTHER: "Other",
};

export type DataQuality = "VERIFIED" | "EXTERNAL" | "USER_ENTERED" | "ESTIMATED";
export type FoodType = "GENERIC" | "BRANDED" | "RECIPE" | "CUSTOM";
export type PreparationState = "RAW" | "COOKED" | "DRY" | "NOT_APPLICABLE";
export type BasisUnit = "G" | "ML";
export type WeightState = "AS_DEFINED" | "COOKED_WEIGHT_OF_RAW_FOOD";
export type Sex = "FEMALE" | "MALE";
export type ActivityLevel = "SEDENTARY" | "LIGHT" | "MODERATE" | "ACTIVE" | "VERY_ACTIVE";
export type Goal = "FAT_LOSS" | "MAINTENANCE" | "MUSCLE_GAIN";
export type DietaryPreference = "VEGETARIAN" | "EGGETARIAN" | "NON_VEGETARIAN" | "VEGAN" | "OTHER";

export interface UserSummary {
  id: number;
  email: string;
  name: string;
  onboardingCompleted: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface Profile {
  userId: number;
  email: string;
  name: string;
  age?: number;
  heightCm?: number;
  currentWeightKg?: number;
  targetWeightKg?: number;
  sex?: Sex;
  activityLevel?: ActivityLevel;
  workoutDaysPerWeek?: number;
  goal?: Goal;
  weightUnit: string;
  heightUnit: string;
  energyUnit: string;
  dietaryPreference?: DietaryPreference;
  allergies?: string;
  dietaryRestrictions?: string;
  calorieTargetKcal?: number;
  proteinTargetG?: number;
  carbsTargetG?: number;
  fatTargetG?: number;
  fiberTargetG?: number;
  waterTargetMl?: number;
  referenceSystem: string;
  timezone: string;
  onboardingCompleted: boolean;
  bmi?: number;
}

export type ProfileUpdate = Omit<
  Profile,
  "userId" | "email" | "bmi" | "onboardingCompleted" | "weightUnit" | "heightUnit" | "energyUnit"
> & {
  onboardingCompleted?: boolean;
};

export interface TargetSuggestion {
  calorieTargetKcal: number;
  proteinTargetG: number;
  carbsTargetG: number;
  fatTargetG: number;
  fiberTargetG: number;
  waterTargetMl: number;
  bmrKcal: number;
  maintenanceKcal: number;
  notes: string[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Portion {
  id?: number;
  label: string;
  unit: string;
  unitQuantity: number;
  basisAmount: number;
  isDefault: boolean;
  sourceNote?: string;
}

export interface FoodSummary {
  id: number;
  name: string;
  brand?: string;
  category?: string;
  foodType: FoodType;
  preparationState: PreparationState;
  basisQuantity: number;
  basisUnit: BasisUnit;
  dataQuality: DataQuality;
  sourceCode?: string;
  completenessPct?: number;
  energyKcal?: number;
  proteinG?: number;
  carbsG?: number;
  fatG?: number;
  defaultPortion?: Portion;
  favorite: boolean;
  owned: boolean;
}

export interface FoodNutrient {
  code: string;
  name: string;
  unit: string;
  category: string;
  formNote?: string;
  amount?: number;
  known: boolean;
  valueOrigin?: string;
  coveragePct?: number;
}

export interface EnergyCheck {
  reportedKcal?: number;
  estimatedKcal?: number;
  differencePct?: number;
  status: "CONSISTENT" | "DIFFERS" | "INSUFFICIENT_DATA";
  note?: string;
}

export interface FoodSource {
  code: string;
  name: string;
  url?: string;
  version?: string;
  licenseNote?: string;
}

export interface FoodDetail extends Omit<FoodSummary, "energyKcal" | "proteinG" | "carbsG" | "fatG" | "defaultPortion" | "sourceCode"> {
  source?: FoodSource;
  sourceFoodRef?: string;
  localNames?: string;
  barcode?: string;
  ediblePortionPct?: number;
  cookedYieldFactor?: number;
  cookedYieldNote?: string;
  notes?: string;
  recipeId?: number;
  nutrients: FoodNutrient[];
  portions: Portion[];
  supportedUnits: string[];
  energyCheck?: EnergyCheck;
}

export interface NutrientDef {
  code: string;
  name: string;
  shortName?: string;
  unit: string;
  category: string;
  formNote?: string;
  displayOrder: number;
}

export interface CustomFoodRequest {
  name: string;
  brand?: string;
  category?: string;
  preparationState?: PreparationState;
  basisQuantity: number;
  basisUnit: BasisUnit;
  nutrients: Record<string, number>;
  portions?: { label: string; unit: string; unitQuantity: number; basisAmount: number }[];
  barcode?: string;
  notes?: string;
}

export interface ProviderStatus {
  code: string;
  name: string;
  enabled: boolean;
  reason?: string;
}

export interface ExternalFoodSummary {
  provider: string;
  externalId: string;
  name: string;
  brand?: string;
  dataType?: string;
  energyKcalPerBasis?: number;
  basisUnit?: BasisUnit;
}

// ---- Diary

export interface MacroTotal {
  amount?: number;
  entriesWithData: number;
  entries: number;
}

export interface Macros {
  energyKcal: MacroTotal;
  proteinG: MacroTotal;
  carbsG: MacroTotal;
  fatG: MacroTotal;
  fiberG: MacroTotal;
}

export interface DiaryEntry {
  id: number;
  foodId: number;
  foodName: string;
  brand?: string;
  dataQuality: DataQuality;
  foodCompletenessPct?: number;
  preparationState: PreparationState;
  mealType: MealType;
  date: string;
  loggedAt: string;
  quantity: number;
  unit: string;
  portionId?: number;
  portionLabel?: string;
  weightState: WeightState;
  basisAmount: number;
  basisUnit: BasisUnit;
  notes?: string;
  energyKcal?: number;
  proteinG?: number;
  carbsG?: number;
  fatG?: number;
  fiberG?: number;
  unknownMacros: string[];
}

export interface DiaryMeal {
  mealType: MealType;
  entries: DiaryEntry[];
  totals: Macros;
}

export interface DiaryDay {
  date: string;
  meals: DiaryMeal[];
  totals: Macros;
  entryCount: number;
  markedComplete: boolean;
  dayNote?: string;
}

export interface EntryRequest {
  foodId: number;
  date: string;
  mealType: MealType;
  quantity: number;
  unit: string;
  portionId?: number | null;
  weightState?: WeightState;
  notes?: string;
}

// ---- Recipes and saved meals

export interface RecipeSummary {
  id: number;
  name: string;
  description?: string;
  servings: number;
  servingWeightG?: number;
  servingLabel?: string;
  energyKcalPerServing?: number;
  proteinGPerServing?: number;
  foodId?: number;
  system: boolean;
  owned: boolean;
  ingredientCount: number;
  updatedAt: string;
}

export interface RecipeIngredient {
  id?: number;
  foodId: number;
  foodName: string;
  dataQuality: DataQuality;
  quantity: number;
  unit: string;
  portionId?: number;
  weightState: WeightState;
  basisAmount: number;
  grams: number;
  energyKcal?: number;
  note?: string;
}

export interface RecipeNutrient {
  code: string;
  name: string;
  unit: string;
  category: string;
  per100g: number;
  perServing: number;
  total: number;
  coveragePct?: number;
}

export interface RecipeNutrition {
  ingredientWeightG: number;
  cookedWeightG: number;
  servings: number;
  servingWeightG: number;
  completenessPct?: number;
  nutrients: RecipeNutrient[];
  missingNutrients: string[];
  warnings: string[];
}

export interface RecipeDetail {
  id: number;
  name: string;
  description?: string;
  instructions?: string;
  servings: number;
  totalCookedWeightG?: number;
  servingLabel?: string;
  foodId?: number;
  system: boolean;
  owned: boolean;
  ingredients: RecipeIngredient[];
  nutrition: RecipeNutrition;
}

export interface IngredientInput {
  foodId: number;
  quantity: number;
  unit: string;
  portionId?: number | null;
  weightState?: WeightState;
  note?: string;
}

export interface RecipeRequest {
  name: string;
  description?: string;
  instructions?: string;
  servings: number;
  totalCookedWeightG?: number | null;
  servingLabel?: string;
  ingredients: IngredientInput[];
}

export interface SavedMealItem {
  id: number;
  foodId: number;
  foodName: string;
  quantity: number;
  unit: string;
  portionId?: number;
  weightState: WeightState;
  basisAmount: number;
  energyKcal?: number;
}

export interface SavedMeal {
  id: number;
  name: string;
  mealType?: MealType;
  notes?: string;
  items: SavedMealItem[];
  energyKcal?: number;
  proteinG?: number;
  carbsG?: number;
  fatG?: number;
}

// ---- Targets and nutrition

export interface TargetSource {
  name: string;
  url?: string;
  version: string;
  valueType: string;
  note?: string;
}

export interface NutrientTarget {
  code: string;
  name: string;
  unit: string;
  category: string;
  formNote?: string;
  target?: number;
  targetType?: string;
  targetSource?: TargetSource;
  upperLimit?: number;
  upperLimitType?: string;
  upperLimitAppliesTo?: string;
  upperLimitComparable: boolean;
  upperLimitSource?: TargetSource;
  customized: boolean;
  notes: string[];
}

export interface TargetsResponse {
  referenceSystem: string;
  age?: number;
  sex?: Sex;
  disclaimer: string;
  nutrients: NutrientTarget[];
}

export interface ReferenceRow {
  nutrientCode: string;
  referenceSystem: string;
  sex: string;
  ageMin: number;
  ageMax: number;
  lifeStage: string;
  valueType: string;
  amount: number;
  appliesTo: string;
  sourceName: string;
  sourceUrl?: string;
  sourceVersion: string;
  notes?: string;
}

export type DataCoverage = "COMPLETE" | "PARTIAL" | "NO_DATA" | "NO_ENTRIES";
export type TargetStatus = "NO_TARGET" | "UNKNOWN" | "BELOW_TARGET" | "TARGET_REACHED" | "ABOVE_UPPER_LIMIT";

export interface NutrientStatus {
  code: string;
  name: string;
  unit: string;
  category: string;
  formNote?: string;
  amount?: number;
  entriesWithData: number;
  entries: number;
  entriesPartial: number;
  coverage: DataCoverage;
  target?: number;
  targetType?: string;
  targetSource?: TargetSource;
  percentOfTarget?: number;
  upperLimit?: number;
  upperLimitType?: string;
  upperLimitAppliesTo?: string;
  upperLimitComparable: boolean;
  percentOfUpperLimit?: number;
  status: TargetStatus;
  customTarget: boolean;
  notes: string[];
}

export interface DailyNutrition {
  date: string;
  entryCount: number;
  disclaimer: string;
  nutrients: NutrientStatus[];
}

export interface MicronutrientView {
  date: string;
  entryCount: number;
  disclaimer: string;
  categories: { category: string; nutrients: number; withData: number }[];
  statusCounts: Record<TargetStatus, number>;
  nutrients: NutrientStatus[];
}

// ---- Weight and water

export interface WeightEntry {
  id: number;
  date: string;
  weightKg: number;
  waistCm?: number;
  bodyFatPct?: number;
  note?: string;
}

export interface WeightStats {
  latestKg?: number;
  latestDate?: string;
  startKg?: number;
  changeKg?: number;
  lowestKg?: number;
  highestKg?: number;
  sevenDayAverageKg?: number;
  targetWeightKg?: number;
  toTargetKg?: number;
}

export interface WeightHistory {
  from: string;
  to: string;
  entries: WeightEntry[];
  stats: WeightStats;
}

export interface WeightRequest {
  date?: string;
  weightKg: number;
  waistCm?: number | null;
  bodyFatPct?: number | null;
  note?: string;
}

export interface WaterEntry {
  id: number;
  date: string;
  loggedAt: string;
  amountMl: number;
  note?: string;
}

export interface WaterDay {
  date: string;
  totalMl: number;
  targetMl?: number;
  percentOfTarget?: number;
  note: string;
  entries: WaterEntry[];
}

export interface WaterHistory {
  from: string;
  to: string;
  targetMl?: number;
  days: { date: string; totalMl: number }[];
}

// ---- Reports

export interface DayRow {
  date: string;
  entries: number;
  energyKcal?: number;
  proteinG?: number;
  carbsG?: number;
  fatG?: number;
  fiberG?: number;
  waterMl: number;
  weightKg?: number;
  completed: boolean;
}

export interface NutrientAverage {
  code: string;
  name: string;
  unit: string;
  category: string;
  average?: number;
  daysWithData: number;
  loggedDays: number;
  target?: number;
  targetType?: string;
  percentOfTarget?: number;
  upperLimit?: number;
  upperLimitComparable: boolean;
  daysAboveUpperLimit: number;
}

export interface RangeReport {
  period: "WEEK" | "MONTH" | "CUSTOM";
  from: string;
  to: string;
  days: number;
  loggedDays: number;
  completedDays: number;
  calorieTargetKcal?: number;
  daysNearCalorieTarget: number;
  averageWaterMl?: number;
  waterTargetMl?: number;
  weight?: { startKg: number; startDate: string; endKg: number; endDate: string; changeKg: number };
  macroSplit?: { proteinPct: number; carbsPct: number; fatPct: number };
  nutrients: NutrientAverage[];
  meals: { mealType: MealType; entries: number; energyKcal?: number }[];
  topFoods: { foodId: number; name: string; brand?: string; timesLogged: number; energyKcal?: number }[];
  dailyRows: DayRow[];
  disclaimer: string;
}

export interface CalendarMonth {
  month: string;
  calorieTargetKcal?: number;
  days: { date: string; entries: number; energyKcal?: number; waterMl: number; weightKg?: number; completed: boolean }[];
}

export interface Trend {
  code: string;
  name: string;
  unit: string;
  target?: number;
  upperLimit?: number;
  from: string;
  to: string;
  points: { date: string; amount?: number; entriesWithData: number; entries: number }[];
}

export interface ApiErrorBody {
  status: number;
  code?: string;
  message?: string;
  fieldErrors?: { field: string; message: string }[];
}
