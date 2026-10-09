# Data sources and methods

NutriTrack only shows nutrient values that come from a named source or are calculated from them.
Nothing is typed in by hand, guessed or randomly generated. Every food and every reference value
carries its source, and the app shows it.

## Food composition

| Source | What's used | Licence and terms | Quality label |
|---|---|---|---|
| **Indian Food Composition Tables 2017** (ICMR-National Institute of Nutrition, Hyderabad) | 99 raw Indian foods: cereals, pulses, vegetables, fruits, dairy, eggs, meat and fish. Read from the open IFCT 2017 dataset (`ifct2017` npm package v2.1.2). | © ICMR-NIN. Cite *Longvah T. et al., Indian Food Composition Tables 2017, NIN-ICMR* when reusing values. | Verified |
| **USDA National Nutrient Database for Standard Reference, Legacy Release 28** (USDA Agricultural Research Service) | 53 foods that IFCT lacks or that need cooked values, such as oils, cooked rice and cooked dal. Read from the SR28 ABBREV file. | Public domain. | Verified |
| **Built-in recipes** | 19 dishes (idli, dosa, sambar, rasam, curd rice, pongal, upma, poriyal, biryani and others). Only ingredient weights are stored; nutrients are calculated from the ingredients above. | NutriTrack recipes. | Estimated |
| **USDA FoodData Central** (optional, needs an API key) | Search and import at the user's request. | Public domain (CC0). | Verified |
| **Open Food Facts** (optional) | Packaged foods by search or barcode. | Open Database License (ODbL); data by Open Food Facts contributors. Imported foods link back to their product page. | Crowd-sourced |
| **Custom foods** | Entered by the user from a label. Visible only to that user. | Not applicable. | User entered |

### Rules applied to food data

- **Unknown is not zero.** A missing value is stored as missing. Totals count how many logged foods
  had a value, and the app shows "No data" or "Incomplete data" instead of a misleading low number.
  The IFCT open dataset encodes "not analysed" as 0, so IFCT zeros are imported as unknown. The only
  zeros added are definitional (cholesterol in plant foods, added sugar in single-ingredient foods)
  and they are marked as derived.
- **Derived values are labelled.** Vitamin A as RAE, folate as DFE, vitamin D as D2 + D3, and total
  carbohydrate from available carbohydrate + fibre are calculated by the seed script, which
  documents each formula.
- **Cooked weights.** Raw foods with a cooked-yield factor can be logged by cooked weight. The
  factor is an estimate from USDA raw/cooked pairs and is labelled as one.
- **Portion weights** for household measures (1 medium idli, 1 katori) are NutriTrack estimates and
  are labelled as estimates.
- **Energy check.** When a food's energy differs a lot from 4/4/9 kcal per gram of protein, carbs and
  fat, the food page shows a note. The source value is never silently replaced.

How the seed migrations are generated is described in [tools/seed/README.md](../tools/seed/README.md).

## Reference values (targets and upper limits)

| Source | Used for |
|---|---|
| **NASEM Dietary Reference Intakes**, from the summary tables published by the NIH Office of Dietary Supplements (DRI reports 1997-2019; sodium and potassium 2019; calcium and vitamin D 2011) | RDA and AI targets, Tolerable Upper Intake Levels, and the sodium Chronic Disease Risk Reduction intake (2,300 mg) |
| **Dietary Guidelines for Americans 2020-2025** (USDA/HHS) | Limits of 10 % of energy for saturated fat and added sugars |

How a target is chosen:

1. A custom value the user set, if any.
2. For calories, protein, carbohydrate, fat, fibre and water: the user's profile targets.
3. The RDA for the user's age and sex, otherwise the AI.
4. If the user hasn't given their sex, a value is used only when it is the same for both sexes.
   Without an age, only values that cover all adults are used.

Upper limits are only compared with food intake when they apply to total intake. Several ULs
(niacin, folate, vitamin E, magnesium, and preformed vitamin A) apply only to supplements, fortified
foods or one chemical form. The app lists them but never flags food against them.

### Not included yet

- **ICMR-NIN Nutrient Requirements for Indians (2020).** These values are not seeded because they
  could not be loaded from an openly licensed machine-readable source. Values are never invented.
  They can be added as rows of `nutrient_reference_values` with `reference_system = 'ICMR_NIN'`,
  with the report as their source.
- **Pregnancy and lactation** reference values.

## What the app doesn't do

- It doesn't diagnose deficiencies or excesses from food logs. Food data is incomplete, and intake
  is not the same as nutritional status.
- It doesn't recommend supplements or doses.
- It doesn't interpret day-to-day weight changes as fat gain or loss. It shows a 7-day average and
  explains that daily changes are mostly water, food and salt.
- It doesn't set a universal water target. The suggested starting point (35 mL/kg) is labelled as a
  rough guide, and the app explains that needs vary with climate, activity, health and food.

Users are encouraged to talk to a doctor or registered dietitian about their own needs.
