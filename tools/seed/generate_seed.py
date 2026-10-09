#!/usr/bin/env python3
"""Generate NutriTrack's Flyway seed migrations from published food composition data.

Inputs (downloaded once, never committed):
  * IFCT 2017 (ICMR-National Institute of Nutrition) as packaged in the `ifct2017` npm
    package, file compositions/index.csv. All values in that file are grams per 100 g
    edible portion and energy is kJ. The package encodes "not analysed" as 0, so this
    script treats IFCT zeros as unknown (see README).
  * USDA National Nutrient Database for Standard Reference, Release 28 (public domain),
    as packaged in the `fda-nutrient-database` npm package, files data/ABBREV.txt and
    data/FOOD_DES.txt.

Usage:
  python3 tools/seed/generate_seed.py --ifct <ifct2017/package> --sr28 <fda-nutrient-database/package>

Outputs (overwritten):
  backend/src/main/resources/db/migration/V2__nutrients_and_references.sql
  backend/src/main/resources/db/migration/V3__seed_foods.sql
  backend/src/main/resources/db/migration/V4__system_recipes.sql

No nutrient value in the output is typed by hand: every food value comes from one of the
two datasets, unit-converted, and every derived value is labelled DERIVED. Recipe dishes
only list ingredient weights; the backend calculates their nutrients at startup with the
same engine it uses for user recipes, and labels them ESTIMATED.
"""
from __future__ import annotations

import argparse
import csv
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"

# --------------------------------------------------------------------------------------
# Nutrient definitions
# code, name, short name, unit, category, form note, FDC nutrient id, SR nutrient number
# --------------------------------------------------------------------------------------
NUTRIENTS = [
    ("ENERGY_KCAL", "Energy", "Calories", "KCAL", "ENERGY", None, 1008, "208"),
    ("PROTEIN", "Protein", "Protein", "G", "MACRONUTRIENT", None, 1003, "203"),
    ("CARBOHYDRATE", "Total carbohydrate", "Carbs", "G", "MACRONUTRIENT", "By difference, includes fibre", 1005, "205"),
    ("AVAILABLE_CARBOHYDRATE", "Available carbohydrate", "Avail. carbs", "G", "MACRONUTRIENT", "Excludes dietary fibre", None, None),
    ("FAT_TOTAL", "Total fat", "Fat", "G", "MACRONUTRIENT", None, 1004, "204"),
    ("FIBER", "Dietary fibre", "Fibre", "G", "MACRONUTRIENT", "Total dietary fibre", 1079, "291"),
    ("SUGARS_TOTAL", "Total sugars", "Sugars", "G", "MACRONUTRIENT", None, 2000, "269"),
    ("SUGARS_ADDED", "Added sugars", "Added sugar", "G", "MACRONUTRIENT", None, 1235, "539"),
    ("CHOLESTEROL", "Cholesterol", "Cholesterol", "MG", "MACRONUTRIENT", None, 1253, "601"),
    ("FAT_SATURATED", "Saturated fat", "Sat. fat", "G", "FATTY_ACID", None, 1258, "606"),
    ("FAT_MONOUNSATURATED", "Monounsaturated fat", "MUFA", "G", "FATTY_ACID", None, 1292, "645"),
    ("FAT_POLYUNSATURATED", "Polyunsaturated fat", "PUFA", "G", "FATTY_ACID", None, 1293, "646"),
    ("FAT_TRANS", "Trans fat", "Trans fat", "G", "FATTY_ACID", None, 1257, "605"),
    ("OMEGA3_TOTAL", "Omega-3 fatty acids", "Omega-3", "G", "FATTY_ACID", "Total n-3 fatty acids", None, None),
    ("OMEGA6_TOTAL", "Omega-6 fatty acids", "Omega-6", "G", "FATTY_ACID", "Total n-6 fatty acids", None, None),
    ("ALA", "Alpha-linolenic acid (ALA)", "ALA", "G", "FATTY_ACID", "18:3 n-3", 1404, "851"),
    ("LINOLEIC_ACID", "Linoleic acid (LA)", "LA", "G", "FATTY_ACID", "18:2 n-6", 1316, "675"),
    ("EPA", "Eicosapentaenoic acid (EPA)", "EPA", "G", "FATTY_ACID", "20:5 n-3", 1278, "629"),
    ("DHA", "Docosahexaenoic acid (DHA)", "DHA", "G", "FATTY_ACID", "22:6 n-3", 1272, "621"),
    ("VITAMIN_A_RAE", "Vitamin A", "Vit A", "UG", "VITAMIN", "Retinol activity equivalents (RAE)", 1106, "320"),
    ("VITAMIN_C", "Vitamin C", "Vit C", "MG", "VITAMIN", "Ascorbic acid", 1162, "401"),
    ("VITAMIN_D", "Vitamin D", "Vit D", "UG", "VITAMIN", "D2 + D3 (1 ug = 40 IU)", 1114, "328"),
    ("VITAMIN_E", "Vitamin E", "Vit E", "MG", "VITAMIN", "Alpha-tocopherol", 1109, "323"),
    ("VITAMIN_K", "Vitamin K", "Vit K", "UG", "VITAMIN", "Phylloquinone (K1)", 1185, "430"),
    ("THIAMIN", "Vitamin B1 (thiamin)", "B1", "MG", "VITAMIN", None, 1165, "404"),
    ("RIBOFLAVIN", "Vitamin B2 (riboflavin)", "B2", "MG", "VITAMIN", None, 1166, "405"),
    ("NIACIN", "Vitamin B3 (niacin)", "B3", "MG", "VITAMIN", "Preformed niacin; targets are in niacin equivalents (NE)", 1167, "406"),
    ("PANTOTHENIC_ACID", "Vitamin B5 (pantothenic acid)", "B5", "MG", "VITAMIN", None, 1170, "410"),
    ("VITAMIN_B6", "Vitamin B6", "B6", "MG", "VITAMIN", None, 1175, "415"),
    ("BIOTIN", "Vitamin B7 (biotin)", "B7", "UG", "VITAMIN", None, 1176, "416"),
    ("FOLATE_DFE", "Vitamin B9 (folate)", "B9", "UG", "VITAMIN", "Dietary folate equivalents (DFE)", 1190, "435"),
    ("VITAMIN_B12", "Vitamin B12", "B12", "UG", "VITAMIN", "Cobalamin", 1178, "418"),
    ("CALCIUM", "Calcium", "Ca", "MG", "MINERAL", None, 1087, "301"),
    ("IRON", "Iron", "Fe", "MG", "MINERAL", None, 1089, "303"),
    ("MAGNESIUM", "Magnesium", "Mg", "MG", "MINERAL", None, 1090, "304"),
    ("PHOSPHORUS", "Phosphorus", "P", "MG", "MINERAL", None, 1091, "305"),
    ("POTASSIUM", "Potassium", "K", "MG", "MINERAL", None, 1092, "306"),
    ("SODIUM", "Sodium", "Na", "MG", "MINERAL", None, 1093, "307"),
    ("ZINC", "Zinc", "Zn", "MG", "MINERAL", None, 1095, "309"),
    ("COPPER", "Copper", "Cu", "MG", "MINERAL", None, 1098, "312"),
    ("MANGANESE", "Manganese", "Mn", "MG", "MINERAL", None, 1101, "315"),
    ("SELENIUM", "Selenium", "Se", "UG", "MINERAL", None, 1103, "317"),
    ("IODINE", "Iodine", "I", "UG", "MINERAL", None, 1100, "314"),
    ("CHROMIUM", "Chromium", "Cr", "UG", "MINERAL", None, 1096, None),
    ("MOLYBDENUM", "Molybdenum", "Mo", "UG", "MINERAL", None, 1102, None),
    ("CHLORIDE", "Chloride", "Cl", "MG", "MINERAL", None, 1088, None),
    ("FLUORIDE", "Fluoride", "F", "MG", "MINERAL", None, 1099, "313"),
    ("CHOLINE", "Choline", "Choline", "MG", "OTHER", "Total choline", 1180, "421"),
    ("WATER", "Water (in food)", "Water", "G", "OTHER", "Moisture contained in food, not drinking water", 1051, "255"),
]

# --------------------------------------------------------------------------------------
# Reference values: NASEM Dietary Reference Intakes as published in the NIH ODS DRI tables.
# Age bands (years): 14-18, 19-30, 31-50, 51-70, 71+. Non-pregnant, non-lactating.
# --------------------------------------------------------------------------------------
BANDS = [(14, 18), (19, 30), (31, 50), (51, 70), (71, 120)]
DRI_SOURCE = "NASEM Dietary Reference Intakes (summary tables published by NIH Office of Dietary Supplements)"
DRI_URL = "https://ods.od.nih.gov/HealthInformation/nutrientrecommendations.aspx"
DRI_VERSION = "DRI 1997-2019 (Na/K 2019, Ca/D 2011)"


def same(v):
    return [v] * 5


# nutrient: list of (value_type, male values per band, female values per band, applies_to, note)
DRI = {
    "PROTEIN": [("RDA", [52, 56, 56, 56, 56], same(46), "TOTAL_INTAKE", "RDA is 0.8 g/kg/day for adults; shown for a reference body weight")],
    "CARBOHYDRATE": [("RDA", same(130), same(130), "TOTAL_INTAKE", "Minimum for brain glucose; not an intake goal")],
    "FIBER": [("AI", [38, 38, 38, 30, 30], [26, 25, 25, 21, 21], "TOTAL_INTAKE", "Based on 14 g per 1000 kcal")],
    "ALA": [("AI", same(1.6), same(1.1), "TOTAL_INTAKE", None)],
    "LINOLEIC_ACID": [("AI", [16, 17, 17, 14, 14], [11, 12, 12, 11, 11], "TOTAL_INTAKE", None)],
    "VITAMIN_A_RAE": [
        ("RDA", same(900), same(700), "TOTAL_INTAKE", None),
        ("UL", [2800, 3000, 3000, 3000, 3000], [2800, 3000, 3000, 3000, 3000], "PREFORMED_ONLY",
         "UL applies to preformed vitamin A (retinol) only, not carotenoids"),
    ],
    "VITAMIN_C": [
        ("RDA", [75, 90, 90, 90, 90], [65, 75, 75, 75, 75], "TOTAL_INTAKE", "Smokers need 35 mg/day more"),
        ("UL", [1800, 2000, 2000, 2000, 2000], [1800, 2000, 2000, 2000, 2000], "TOTAL_INTAKE", None),
    ],
    "VITAMIN_D": [
        ("RDA", [15, 15, 15, 15, 20], [15, 15, 15, 15, 20], "TOTAL_INTAKE", "Assumes minimal sun exposure"),
        ("UL", same(100), same(100), "TOTAL_INTAKE", None),
    ],
    "VITAMIN_E": [
        ("RDA", same(15), same(15), "TOTAL_INTAKE", None),
        ("UL", [800, 1000, 1000, 1000, 1000], [800, 1000, 1000, 1000, 1000], "SUPPLEMENTAL_ONLY",
         "UL applies to supplemental alpha-tocopherol only"),
    ],
    "VITAMIN_K": [("AI", [75, 120, 120, 120, 120], [75, 90, 90, 90, 90], "TOTAL_INTAKE", None)],
    "THIAMIN": [("RDA", same(1.2), [1.0, 1.1, 1.1, 1.1, 1.1], "TOTAL_INTAKE", None)],
    "RIBOFLAVIN": [("RDA", same(1.3), [1.0, 1.1, 1.1, 1.1, 1.1], "TOTAL_INTAKE", None)],
    "NIACIN": [
        ("RDA", same(16), same(14), "TOTAL_INTAKE", "In niacin equivalents; food data reports preformed niacin, which understates NE"),
        ("UL", [30, 35, 35, 35, 35], [30, 35, 35, 35, 35], "SUPPLEMENTAL_ONLY", "UL applies to supplements and fortified foods"),
    ],
    "PANTOTHENIC_ACID": [("AI", same(5), same(5), "TOTAL_INTAKE", None)],
    "VITAMIN_B6": [
        ("RDA", [1.3, 1.3, 1.3, 1.7, 1.7], [1.2, 1.3, 1.3, 1.5, 1.5], "TOTAL_INTAKE", None),
        ("UL", [80, 100, 100, 100, 100], [80, 100, 100, 100, 100], "TOTAL_INTAKE", None),
    ],
    "BIOTIN": [("AI", [25, 30, 30, 30, 30], [25, 30, 30, 30, 30], "TOTAL_INTAKE", None)],
    "FOLATE_DFE": [
        ("RDA", same(400), same(400), "TOTAL_INTAKE", None),
        ("UL", [800, 1000, 1000, 1000, 1000], [800, 1000, 1000, 1000, 1000], "SUPPLEMENTAL_ONLY",
         "UL applies to synthetic folic acid from supplements and fortified foods"),
    ],
    "VITAMIN_B12": [("RDA", same(2.4), same(2.4), "TOTAL_INTAKE", None)],
    "CHOLINE": [
        ("AI", same(550), [400, 425, 425, 425, 425], "TOTAL_INTAKE", None),
        ("UL", [3000, 3500, 3500, 3500, 3500], [3000, 3500, 3500, 3500, 3500], "TOTAL_INTAKE", None),
    ],
    "CALCIUM": [
        ("RDA", [1300, 1000, 1000, 1000, 1200], [1300, 1000, 1000, 1200, 1200], "TOTAL_INTAKE", None),
        ("UL", [3000, 2500, 2500, 2000, 2000], [3000, 2500, 2500, 2000, 2000], "TOTAL_INTAKE", None),
    ],
    "IRON": [
        ("RDA", [11, 8, 8, 8, 8], [15, 18, 18, 8, 8], "TOTAL_INTAKE", "Vegetarians may need about 1.8 times more"),
        ("UL", same(45), same(45), "TOTAL_INTAKE", None),
    ],
    "MAGNESIUM": [
        ("RDA", [410, 400, 420, 420, 420], [360, 310, 320, 320, 320], "TOTAL_INTAKE", None),
        ("UL", same(350), same(350), "SUPPLEMENTAL_ONLY", "UL applies to supplements and medications, not food"),
    ],
    "PHOSPHORUS": [
        ("RDA", [1250, 700, 700, 700, 700], [1250, 700, 700, 700, 700], "TOTAL_INTAKE", None),
        ("UL", [4000, 4000, 4000, 4000, 3000], [4000, 4000, 4000, 4000, 3000], "TOTAL_INTAKE", None),
    ],
    "POTASSIUM": [("AI", [3000, 3400, 3400, 3400, 3400], [2300, 2600, 2600, 2600, 2600], "TOTAL_INTAKE", None)],
    "SODIUM": [
        ("AI", same(1500), same(1500), "TOTAL_INTAKE", None),
        ("CDRR", same(2300), same(2300), "TOTAL_INTAKE", "Chronic Disease Risk Reduction intake: reduce intake if above this"),
    ],
    "ZINC": [
        ("RDA", same(11), [9, 8, 8, 8, 8], "TOTAL_INTAKE", None),
        ("UL", [34, 40, 40, 40, 40], [34, 40, 40, 40, 40], "TOTAL_INTAKE", None),
    ],
    "COPPER": [
        ("RDA", [0.89, 0.9, 0.9, 0.9, 0.9], [0.89, 0.9, 0.9, 0.9, 0.9], "TOTAL_INTAKE", None),
        ("UL", [8, 10, 10, 10, 10], [8, 10, 10, 10, 10], "TOTAL_INTAKE", None),
    ],
    "MANGANESE": [
        ("AI", [2.2, 2.3, 2.3, 2.3, 2.3], [1.6, 1.8, 1.8, 1.8, 1.8], "TOTAL_INTAKE", None),
        ("UL", [9, 11, 11, 11, 11], [9, 11, 11, 11, 11], "TOTAL_INTAKE", None),
    ],
    "SELENIUM": [
        ("RDA", same(55), same(55), "TOTAL_INTAKE", None),
        ("UL", same(400), same(400), "TOTAL_INTAKE", None),
    ],
    "IODINE": [
        ("RDA", same(150), same(150), "TOTAL_INTAKE", "Iodised salt is a major source in India and is rarely captured in food data"),
        ("UL", [900, 1100, 1100, 1100, 1100], [900, 1100, 1100, 1100, 1100], "TOTAL_INTAKE", None),
    ],
    "CHROMIUM": [("AI", [35, 35, 35, 30, 30], [24, 25, 25, 20, 20], "TOTAL_INTAKE", None)],
    "MOLYBDENUM": [
        ("RDA", [43, 45, 45, 45, 45], [43, 45, 45, 45, 45], "TOTAL_INTAKE", None),
        ("UL", [1700, 2000, 2000, 2000, 2000], [1700, 2000, 2000, 2000, 2000], "TOTAL_INTAKE", None),
    ],
    "CHLORIDE": [
        ("AI", [2300, 2300, 2300, 2000, 1800], [2300, 2300, 2300, 2000, 1800], "TOTAL_INTAKE", None),
        ("UL", same(3600), same(3600), "TOTAL_INTAKE", None),
    ],
    "FLUORIDE": [
        ("AI", [3, 4, 4, 4, 4], same(3), "TOTAL_INTAKE", None),
        ("UL", same(10), same(10), "TOTAL_INTAKE", None),
    ],
}

DGA_SOURCE = "Dietary Guidelines for Americans, 2020-2025 (USDA/HHS)"
DGA_URL = "https://www.dietaryguidelines.gov/"
DGA_VERSION = "2020-2025 edition"
DGA_LIMITS = {
    "FAT_SATURATED": (10, "Limit: less than 10% of daily calories"),
    "SUGARS_ADDED": (10, "Limit: less than 10% of daily calories"),
}

# --------------------------------------------------------------------------------------
# Seed food selection
# --------------------------------------------------------------------------------------
IFCT_FOODS = {
    # code: (display name override or None, category override or None)
    "A015": ("Rice, white, raw (milled)", None),
    "A014": ("Rice, parboiled, raw (milled)", None),
    "A013": ("Rice, brown, raw", None),
    "A011": ("Rice flakes (poha / aval)", None),
    "A012": ("Rice, puffed (pori / murmura)", None),
    "A010": ("Ragi (finger millet), whole grain", None),
    "A003": ("Bajra (pearl millet / kambu)", None),
    "A005": ("Jowar (sorghum / cholam)", None),
    "A016": ("Samai (little millet)", None),
    "A017": ("Thinai / varagu (millet)", None),
    "A019": ("Wheat flour, whole (atta)", None),
    "A018": ("Wheat flour, refined (maida)", None),
    "A022": ("Wheat semolina (rava / sooji)", None),
    "A023": ("Wheat vermicelli (semiya)", None),
    "B003": ("Black gram dal (urad dal / ulundu)", None),
    "B004": ("Black gram, whole", None),
    "B001": ("Bengal gram dal (chana dal / kadalai paruppu)", None),
    "B002": ("Bengal gram, whole (chickpea / kondakadalai)", None),
    "B021": ("Red gram dal (toor dal / thuvaram paruppu)", None),
    "B010": ("Green gram dal (moong dal / pasi paruppu)", None),
    "B011": ("Green gram, whole (moong / pachai payaru)", None),
    "B013": ("Lentil dal (masoor dal)", None),
    "B012": ("Horse gram, whole (kollu)", None),
    "B025": ("Soya bean, white", None),
    "C033": ("Spinach", None),
    "C019": ("Drumstick leaves (murungai keerai)", None),
    "C015": ("Cabbage, green", None),
    "D031": ("Brinjal (eggplant / kathirikai)", None),
    "D056": ("Ladies finger (okra / vendakkai)", None),
    "D046": ("Drumstick (murungakkai)", None),
    "D036": ("Cauliflower", None),
    "D049": ("French beans", None),
    "D068": ("Ridge gourd (peerkangai)", None),
    "D007": ("Bottle gourd (suraikkai)", None),
    "D004": ("Bitter gourd (pavakkai)", None),
    "D066": ("Pumpkin, orange (parangikai)", None),
    "D043": ("Cucumber", None),
    "D076": ("Tomato, ripe", None),
    "G017": ("Onion, big", None),
    "G018": ("Onion, small (shallot / sambar onion)", None),
    "G008": ("Green chillies", None),
    "G009": ("Coriander leaves", None),
    "G010": ("Curry leaves", None),
    "G011": ("Garlic", None),
    "G014": ("Ginger, fresh", None),
    "G022": ("Red chillies, dry", None),
    "G024": ("Coriander seeds", None),
    "G025": ("Cumin seeds", None),
    "G033": ("Turmeric powder", None),
    "F002": ("Carrot", None),
    "F006": ("Potato", None),
    "F013": ("Sweet potato", None),
    "E012": ("Banana, ripe, robusta", None),
    "E010": ("Banana, ripe, poovan", None),
    "E036": ("Mango, ripe, banganapalli", None),
    "E028": ("Guava, white flesh", None),
    "E049": ("Papaya, ripe", None),
    "E047": ("Orange, pulp", None),
    "E001": ("Apple", None),
    "E065": ("Watermelon", None),
    "E053": ("Pineapple", None),
    "E064": ("Tamarind pulp", None),
    "H001": ("Almonds", None),
    "H005": ("Cashew nuts", None),
    "H012": ("Groundnuts (peanuts)", None),
    "H007": ("Coconut, fresh kernel", None),
    "H006": ("Coconut, dry kernel (copra)", None),
    "H011": ("Sesame seeds (gingelly), white", None),
    "H014": ("Flaxseeds (linseeds)", None),
    "H013": ("Mustard seeds", None),
    "I001": ("Jaggery, cane", None),
    "K002": ("Coconut water", None),
    "L002": ("Milk, cow, whole", None),
    "L001": ("Milk, buffalo, whole", None),
    "L003": ("Paneer", None),
    "M001": ("Egg, whole, raw", None),
    "M004": ("Egg, whole, boiled", None),
    "M007": ("Egg omelette", None),
    "M002": ("Egg white, raw", None),
    "N003": ("Chicken breast, skinless, raw", None),
    "N001": ("Chicken leg, skinless, raw", None),
    "N002": ("Chicken thigh, skinless, raw", None),
    "N005": ("Chicken liver, raw", None),
    "O003": ("Mutton (goat), leg, raw", None),
    "O001": ("Mutton (goat), shoulder, raw", None),
    "P087": ("Vanjaram (seer fish / king mackerel), raw", None),
    "P071": ("Sardine (mathi / chaala), raw", None),
    "P034": ("Mackerel (ayala / kanangeluthi), raw", None),
    "P084": ("Tuna, raw", None),
    "P057": ("Pomfret, white (vaval), raw", None),
    "P065": ("Red snapper, raw", None),
    "P060": ("Queen fish (para), raw", None),
    "P083": ("Tilapia, raw", None),
    "S006": ("Rohu, raw", None),
    "S008": ("Prawns, big, raw", None),
    "S009": ("Prawns, small, raw", None),
    "Q007": ("Tiger prawns, raw", None),
    "Q002": ("Crab, sea, raw", None),
    "R004": ("Squid, raw", None),
}
IFCT_PLANT_GROUPS = {
    "Cereals and Millets", "Grain Legumes", "Green Leafy Vegetables", "Other Vegetables", "Fruits",
    "Roots and Tubers", "Condiments and Spices", "Nuts and Oil Seeds", "Sugars", "Mushrooms",
}

SR28_FOODS = {
    # NDB: (display name, category, preparation state)
    "20445": ("Rice, white, cooked (boiled, unsalted)", "Cereals and Millets", "COOKED"),
    "20444": ("Rice, white, long-grain, raw (USDA)", "Cereals and Millets", "RAW"),
    "20037": ("Rice, brown, cooked", "Cereals and Millets", "COOKED"),
    "08120": ("Oats, rolled, dry", "Cereals and Millets", "DRY"),
    "08121": ("Oats, cooked with water", "Cereals and Millets", "COOKED"),
    "18069": ("Bread, white", "Bakery", "NOT_APPLICABLE"),
    "16070": ("Lentils, boiled", "Grain Legumes", "COOKED"),
    "16069": ("Lentils, raw (USDA)", "Grain Legumes", "RAW"),
    "16102": ("Toor dal / pigeon peas, boiled", "Grain Legumes", "COOKED"),
    "16101": ("Pigeon peas (red gram), raw (USDA)", "Grain Legumes", "RAW"),
    "16081": ("Moong (mung beans), boiled", "Grain Legumes", "COOKED"),
    "16080": ("Mung beans, raw (USDA)", "Grain Legumes", "RAW"),
    "16057": ("Chickpeas (chana), boiled", "Grain Legumes", "COOKED"),
    "16033": ("Rajma (kidney beans), boiled", "Grain Legumes", "COOKED"),
    "16426": ("Tofu, firm", "Grain Legumes", "NOT_APPLICABLE"),
    "05062": ("Chicken breast, skinless, raw (USDA)", "Poultry", "RAW"),
    "05064": ("Chicken breast, cooked, roasted", "Poultry", "COOKED"),
    "01123": ("Egg, whole, raw (USDA)", "Egg and Egg Products", "RAW"),
    "01129": ("Egg, whole, hard-boiled (USDA)", "Egg and Egg Products", "COOKED"),
    "01132": ("Egg, scrambled (USDA)", "Egg and Egg Products", "COOKED"),
    "15270": ("Shrimp / prawns, raw (USDA)", "Marine Shellfish", "RAW"),
    "15271": ("Shrimp / prawns, cooked", "Marine Shellfish", "COOKED"),
    "15051": ("Mackerel, Spanish, raw (USDA)", "Marine Fish", "RAW"),
    "15052": ("Mackerel, Spanish, cooked, dry heat", "Marine Fish", "COOKED"),
    "15261": ("Tilapia, raw (USDA)", "Marine Fish", "RAW"),
    "15262": ("Tilapia, cooked, dry heat", "Marine Fish", "COOKED"),
    "15076": ("Salmon, Atlantic, wild, raw", "Marine Fish", "RAW"),
    "01077": ("Milk, whole, 3.25% fat (vitamin D added)", "Milk and Milk Products", "NOT_APPLICABLE"),
    "01079": ("Milk, toned / 2% fat (vitamins A and D added)", "Milk and Milk Products", "NOT_APPLICABLE"),
    "01085": ("Milk, skimmed (vitamins A and D added)", "Milk and Milk Products", "NOT_APPLICABLE"),
    "01116": ("Curd / plain yogurt, whole milk", "Milk and Milk Products", "NOT_APPLICABLE"),
    "01009": ("Cheese, cheddar", "Milk and Milk Products", "NOT_APPLICABLE"),
    "01001": ("Butter, salted", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "01003": ("Ghee (butter oil, anhydrous)", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04042": ("Groundnut oil (peanut oil)", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04058": ("Gingelly oil (sesame oil / nallennai)", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04047": ("Coconut oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04506": ("Sunflower oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04044": ("Soybean oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04583": ("Mustard oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04037": ("Rice bran oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "04053": ("Olive oil", "Edible Oils and Fats", "NOT_APPLICABLE"),
    "12061": ("Almonds (USDA)", "Nuts and Oil Seeds", "NOT_APPLICABLE"),
    "16098": ("Peanut butter, smooth, salted", "Nuts and Oil Seeds", "NOT_APPLICABLE"),
    "12006": ("Chia seeds", "Nuts and Oil Seeds", "NOT_APPLICABLE"),
    "09040": ("Banana (USDA)", "Fruits", "RAW"),
    "09003": ("Apple, with skin (USDA)", "Fruits", "RAW"),
    "11352": ("Potato, raw, with skin (USDA)", "Roots and Tubers", "RAW"),
    "19335": ("Sugar, white (granulated)", "Sugars", "NOT_APPLICABLE"),
    "19296": ("Honey", "Sugars", "NOT_APPLICABLE"),
    "02047": ("Salt, table", "Condiments and Spices", "NOT_APPLICABLE"),
    "14355": ("Tea, black, brewed (no milk or sugar)", "Beverages", "NOT_APPLICABLE"),
    "14209": ("Coffee, brewed (no milk or sugar)", "Beverages", "NOT_APPLICABLE"),
}

# Raw -> cooked pairs within SR28, used to estimate a cooked-yield factor for the raw food by
# a solids mass balance: cooked/raw = (100 - water_raw) / (100 - water_cooked). This assumes
# solids are retained; drip losses from meat and fish make it an estimate.
SR28_YIELD_PAIRS = {
    "20444": ("20445", "boiled"),
    "16069": ("16070", "boiled"),
    "16101": ("16102", "boiled"),
    "16080": ("16081", "boiled"),
    "08120": ("08121", "cooked with water"),
    "05062": ("05064", "roasted"),
    "15270": ("15271", "cooked"),
    "15051": ("15052", "cooked, dry heat"),
    "15261": ("15262", "cooked, dry heat"),
}
# IFCT raw foods that may borrow an SR28 yield estimate for the same commodity.
IFCT_BORROWED_YIELDS = {"A015": "20444", "N003": "05062", "S008": "15270", "S009": "15270", "Q007": "15270",
                        "B021": "16101", "B010": "16080", "B013": "16069"}

# Typical household portions for IFCT foods. These are portion weights only (no nutrient values).
TYPICAL_PORTIONS = {
    "M001": [("1 large egg (edible part)", "PIECE", 50)],
    "M004": [("1 large egg (edible part)", "PIECE", 50)],
    "M007": [("1 omelette (1 egg)", "PIECE", 60)],
    "M002": [("1 large egg white", "PIECE", 33)],
    "E012": [("1 medium banana (peeled)", "PIECE", 100)],
    "E010": [("1 poovan banana (peeled)", "PIECE", 60)],
    "H001": [("10 almonds", "SERVING", 12)],
    "H005": [("10 cashews", "SERVING", 15)],
    "L002": [("1 glass (200 mL)", "SERVING", 206)],
    "L001": [("1 glass (200 mL)", "SERVING", 206)],
    "L003": [("1 cube (25 g)", "PIECE", 25)],
    "G008": [("1 green chilli", "PIECE", 3)],
    "G011": [("1 clove", "PIECE", 3)],
    "D076": [("1 medium tomato", "PIECE", 100)],
    "G017": [("1 medium onion", "PIECE", 110)],
    "I001": [("1 tbsp grated", "TBSP", 12)],
    "K002": [("1 glass (200 mL)", "SERVING", 204)],
}
TYPICAL_PORTION_NOTE = "Typical household portion weight (NutriTrack estimate)"


def dec(value) -> Decimal | None:
    if value is None:
        return None
    s = str(value).strip()
    if s == "":
        return None
    return Decimal(s)


def q(value: Decimal, places: str = "0.000001") -> str:
    return str(value.quantize(Decimal(places), rounding=ROUND_HALF_UP).normalize()) if value != 0 else "0"


def sql_str(s: str | None) -> str:
    if s is None:
        return "NULL"
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_num(v) -> str:
    return "NULL" if v is None else str(v)


# ------------------------------- IFCT mapping ----------------------------------------
G_TO = {"G": Decimal(1), "MG": Decimal(1000), "UG": Decimal(1000000)}


def ifct_nutrients(r: dict) -> dict[str, tuple[Decimal, str]]:
    """Map one IFCT row (grams per 100 g) to canonical units. Zeros are treated as unknown."""

    def v(col):
        x = dec(r.get(col))
        return None if x is None or x == 0 else x

    out: dict[str, tuple[Decimal, str]] = {}

    def put(code, value, origin="REPORTED"):
        if value is not None:
            out[code] = (value, origin)

    energy_kj = v("enerc")
    put("ENERGY_KCAL", energy_kj / Decimal("4.184") if energy_kj else None)
    put("WATER", v("water"))
    put("PROTEIN", v("protcnt"))
    put("FAT_TOTAL", v("fatce"))
    put("FIBER", v("fibtg"))
    avail = v("choavldf")
    put("AVAILABLE_CARBOHYDRATE", avail)
    if avail is not None and v("fibtg") is not None:
        put("CARBOHYDRATE", avail + v("fibtg"), "DERIVED")
    put("SUGARS_TOTAL", v("fsugar"))
    chol = v("cholc")
    put("CHOLESTEROL", chol * 1000 if chol else None)
    for code, col in [("FAT_SATURATED", "fasat"), ("FAT_MONOUNSATURATED", "fams"), ("FAT_POLYUNSATURATED", "fapu"),
                      ("FAT_TRANS", "fatrn"), ("OMEGA3_TOTAL", "facn3"), ("OMEGA6_TOTAL", "facn6"),
                      ("ALA", "f18d3n3"), ("LINOLEIC_ACID", "f18d2cn6"), ("EPA", "f20d5n3"), ("DHA", "f22d6n3")]:
        put(code, v(col))
    # Vitamin A as RAE (IOM 2001): retinol + beta-carotene/12 + (alpha-carotene + beta-cryptoxanthin)/24
    retol, cartb, carta, crypx = v("retol"), v("cartb"), v("carta"), v("crypxb")
    if retol is not None or cartb is not None:
        Z = Decimal(0)
        rae = (retol or Z) + (cartb or Z) / 12 + ((carta or Z) + (crypx or Z)) / 24
        put("VITAMIN_A_RAE", rae * G_TO["UG"], "DERIVED")
    d2, d3 = v("ergcal"), v("chocal")
    if d2 is not None or d3 is not None:
        put("VITAMIN_D", ((d2 or Decimal(0)) + (d3 or Decimal(0))) * G_TO["UG"], "DERIVED")
    simple = [("VITAMIN_C", "vitc", "MG"), ("VITAMIN_E", "tocpha", "MG"), ("VITAMIN_K", "vitk1", "UG"),
              ("THIAMIN", "thia", "MG"), ("RIBOFLAVIN", "ribf", "MG"), ("NIACIN", "nia", "MG"),
              ("PANTOTHENIC_ACID", "pantac", "MG"), ("VITAMIN_B6", "vitb6c", "MG"), ("BIOTIN", "biot", "UG"),
              ("CALCIUM", "ca", "MG"), ("IRON", "fe", "MG"), ("MAGNESIUM", "mg", "MG"), ("PHOSPHORUS", "p", "MG"),
              ("POTASSIUM", "k", "MG"), ("SODIUM", "na", "MG"), ("ZINC", "zn", "MG"), ("COPPER", "cu", "MG"),
              ("MANGANESE", "mn", "MG"), ("SELENIUM", "se", "UG"), ("CHROMIUM", "cr", "UG"),
              ("MOLYBDENUM", "mo", "UG")]
    for code, col, unit in simple:
        x = v(col)
        put(code, x * G_TO[unit] if x is not None else None)
    fol = v("folsum")
    # Unfortified foods contain only food folate, for which DFE equals micrograms of food folate.
    put("FOLATE_DFE", fol * G_TO["UG"] if fol is not None else None, "DERIVED")
    if r["grup"] in IFCT_PLANT_GROUPS and "CHOLESTEROL" not in out:
        put("CHOLESTEROL", Decimal(0), "DERIVED")
    return out


# ------------------------------- SR28 mapping ----------------------------------------
SR_COLUMNS = [
    "NDB_No", "Shrt_Desc", "Water", "Energ_Kcal", "Protein", "Lipid_Tot", "Ash", "Carbohydrt", "Fiber_TD",
    "Sugar_Tot", "Calcium", "Iron", "Magnesium", "Phosphorus", "Potassium", "Sodium", "Zinc", "Copper",
    "Manganese", "Selenium", "Vit_C", "Thiamin", "Riboflavin", "Niacin", "Panto_Acid", "Vit_B6", "Folate_Tot",
    "Folic_Acid", "Food_Folate", "Folate_DFE", "Choline_Tot", "Vit_B12", "Vit_A_IU", "Vit_A_RAE", "Retinol",
    "Alpha_Carot", "Beta_Carot", "Beta_Crypt", "Lycopene", "Lut_Zea", "Vit_E", "Vit_D_mcg", "Vit_D_IU", "Vit_K",
    "FA_Sat", "FA_Mono", "FA_Poly", "Cholestrl", "GmWt_1", "GmWt_Desc1", "GmWt_2", "GmWt_Desc2", "Refuse_Pct",
]
SR_MAP = {
    "Water": "WATER", "Energ_Kcal": "ENERGY_KCAL", "Protein": "PROTEIN", "Lipid_Tot": "FAT_TOTAL",
    "Carbohydrt": "CARBOHYDRATE", "Fiber_TD": "FIBER", "Sugar_Tot": "SUGARS_TOTAL", "Calcium": "CALCIUM",
    "Iron": "IRON", "Magnesium": "MAGNESIUM", "Phosphorus": "PHOSPHORUS", "Potassium": "POTASSIUM",
    "Sodium": "SODIUM", "Zinc": "ZINC", "Copper": "COPPER", "Manganese": "MANGANESE", "Selenium": "SELENIUM",
    "Vit_C": "VITAMIN_C", "Thiamin": "THIAMIN", "Riboflavin": "RIBOFLAVIN", "Niacin": "NIACIN",
    "Panto_Acid": "PANTOTHENIC_ACID", "Vit_B6": "VITAMIN_B6", "Folate_DFE": "FOLATE_DFE", "Choline_Tot": "CHOLINE",
    "Vit_B12": "VITAMIN_B12", "Vit_A_RAE": "VITAMIN_A_RAE", "Vit_E": "VITAMIN_E", "Vit_D_mcg": "VITAMIN_D",
    "Vit_K": "VITAMIN_K", "FA_Sat": "FAT_SATURATED", "FA_Mono": "FAT_MONOUNSATURATED",
    "FA_Poly": "FAT_POLYUNSATURATED", "Cholestrl": "CHOLESTEROL",
}


def read_sr28(path: Path):
    abbrev = {}
    with open(path / "data" / "ABBREV.txt", encoding="latin-1") as fh:
        for line in fh:
            parts = [p.strip("~") for p in line.rstrip("\r\n").split("^")]
            if len(parts) != len(SR_COLUMNS):
                raise ValueError(f"Unexpected SR28 ABBREV row width {len(parts)}")
            abbrev[parts[0]] = dict(zip(SR_COLUMNS, parts))
    names = {}
    with open(path / "data" / "FOOD_DES.txt", encoding="latin-1") as fh:
        for line in fh:
            parts = [p.strip("~") for p in line.rstrip("\r\n").split("^")]
            names[parts[0]] = parts[2]
    return abbrev, names


def sr_nutrients(row: dict) -> dict[str, tuple[Decimal, str]]:
    out = {}
    for col, code in SR_MAP.items():
        x = dec(row[col])
        if x is not None:
            out[code] = (x, "REPORTED")
    if "CARBOHYDRATE" in out and "FIBER" in out:
        avail = out["CARBOHYDRATE"][0] - out["FIBER"][0]
        if avail >= 0:
            out["AVAILABLE_CARBOHYDRATE"] = (avail, "DERIVED")
    return out


UNIT_WORDS = {"cup": "CUP", "cups": "CUP", "tbsp": "TBSP", "tsp": "TSP", "slice": "SLICE", "slices": "SLICE"}


def sr_portions(row: dict) -> list[tuple[str, str, Decimal, Decimal]]:
    """Return (label, unit, unit_quantity, grams) household measures from ABBREV GmWt fields."""
    out = []
    for gcol, dcol in (("GmWt_1", "GmWt_Desc1"), ("GmWt_2", "GmWt_Desc2")):
        grams, desc = dec(row[gcol]), row[dcol].strip()
        if not grams or not desc:
            continue
        first, _, rest = desc.partition(" ")
        try:
            qty = Decimal(first)
        except Exception:
            qty, rest = Decimal(1), desc
        unit_word = rest.split(",")[0].split(" ")[0].lower()
        unit = UNIT_WORDS.get(unit_word, "PIECE" if rest else "SERVING")
        label = desc if not desc.startswith(".") else "0" + desc
        out.append((label[:100], unit, qty, grams))
    return out


# ------------------------------- System recipes ---------------------------------------
# Ingredient weights for standard home recipes (grams, as purchased edible portion), the
# weighed cooked yield, and the serving label/weight. The backend computes the nutrients.
# ("SRC", ref) refers to a seeded food: SRC is IFCT_2017 or USDA_SR28.
I, S = "IFCT_2017", "USDA_SR28"
RECIPES = [
    ("idli", "Idli", "Steamed fermented rice and urad dal cakes, Tamil Nadu style (3:1 parboiled rice to urad dal).",
     950, "1 medium idli", 40,
     [((I, "A014"), 300), ((I, "B003"), 100), ((S, "02047"), 6)]),
    ("plain_dosa", "Dosa, plain", "Fermented rice and urad dal crepe cooked with a little groundnut oil.",
     840, "1 medium dosa", 60,
     [((I, "A014"), 200), ((I, "A015"), 100), ((I, "B003"), 100), ((S, "04042"), 40), ((S, "02047"), 6)]),
    ("sambar", "Sambar (mixed vegetable)", "Toor dal and tamarind stew with drumstick, brinjal and shallots.",
     1500, "1 katori (150 g)", 150,
     [((I, "B021"), 100), ((I, "D046"), 100), ((I, "D031"), 100), ((I, "G018"), 100), ((I, "D076"), 100),
      ((I, "E064"), 20), ((I, "G022"), 5), ((I, "G024"), 10), ((I, "G033"), 2), ((S, "04058"), 15),
      ((I, "H013"), 3), ((I, "G010"), 5), ((S, "02047"), 10)]),
    ("rasam", "Rasam (tomato)", "Thin tamarind and tomato soup tempered with ghee, cumin and garlic.",
     1000, "1 cup (200 g)", 200,
     [((I, "D076"), 200), ((I, "E064"), 20), ((I, "B021"), 25), ((I, "G025"), 5), ((I, "G011"), 10),
      ((I, "G022"), 3), ((I, "G010"), 5), ((I, "G009"), 10), ((S, "01003"), 10), ((I, "H013"), 2),
      ((S, "02047"), 8)]),
    ("curd_rice", "Curd rice (thayir sadam)", "Cooked rice mixed with curd and milk, with a mustard and curry leaf tempering.",
     625, "1 bowl (250 g)", 250,
     [((S, "20445"), 300), ((S, "01116"), 250), ((S, "01077"), 50), ((S, "04042"), 5), ((I, "H013"), 2),
      ((I, "G010"), 3), ((I, "G008"), 5), ((I, "G014"), 5), ((S, "02047"), 4)]),
    ("chapati", "Chapati / roti", "Whole wheat flatbread with a little oil.",
     300, "1 medium chapati", 40,
     [((I, "A019"), 200), ((S, "04506"), 10), ((S, "02047"), 2)]),
    ("chicken_curry", "Chicken curry (Tamil style)", "Chicken leg pieces cooked with onion, tomato, coconut and spices.",
     900, "1 katori (150 g)", 150,
     [((I, "N001"), 500), ((I, "G017"), 200), ((I, "D076"), 150), ((I, "G014"), 15), ((I, "G011"), 15),
      ((I, "G022"), 8), ((I, "G024"), 10), ((I, "G033"), 2), ((I, "H007"), 50), ((S, "04042"), 40),
      ((I, "G010"), 5), ((S, "02047"), 10)]),
    ("chicken_biryani", "Chicken biryani", "Rice layered with spiced chicken, curd, onion, ghee and oil.",
     1900, "1 plate (300 g)", 300,
     [((I, "A015"), 400), ((I, "N001"), 500), ((I, "G017"), 200), ((I, "D076"), 100), ((S, "01116"), 100),
      ((S, "01003"), 30), ((S, "04506"), 30), ((I, "G014"), 15), ((I, "G011"), 15), ((I, "G008"), 10),
      ((I, "G009"), 15), ((S, "02047"), 12)]),
    ("dal_tadka", "Dal (paruppu / dal tadka)", "Toor dal cooked soft and tempered with ghee, cumin and garlic.",
     900, "1 katori (150 g)", 150,
     [((I, "B021"), 150), ((I, "G017"), 50), ((I, "D076"), 75), ((S, "01003"), 15), ((I, "G025"), 3),
      ((I, "G011"), 5), ((I, "G033"), 2), ((S, "02047"), 6)]),
    ("vanjaram_fry", "Vanjaram fish fry", "Seer fish slices marinated in chilli, turmeric, ginger and garlic, shallow fried.",
     420, "1 slice (85 g)", 85,
     [((I, "P087"), 500), ((I, "G022"), 10), ((I, "G033"), 2), ((I, "G014"), 10), ((I, "G011"), 10),
      ((S, "02047"), 6), ((S, "04042"), 30)]),
    ("prawn_masala", "Prawn masala (eral thokku)", "Prawns cooked with onion, tomato, ginger, garlic and chilli.",
     550, "1 katori (110 g)", 110,
     [((I, "S008"), 400), ((I, "G017"), 150), ((I, "D076"), 100), ((S, "04042"), 30), ((I, "G014"), 10),
      ((I, "G011"), 10), ((I, "G022"), 8), ((I, "G033"), 2), ((S, "02047"), 6)]),
    ("cabbage_poriyal", "Cabbage poriyal", "Stir-fried cabbage with grated coconut and a mustard, urad dal tempering.",
     300, "1 katori (100 g)", 100,
     [((I, "C015"), 300), ((I, "H007"), 30), ((S, "04042"), 10), ((I, "H013"), 2), ((I, "B003"), 5),
      ((I, "G008"), 5), ((I, "G010"), 3), ((S, "02047"), 4)]),
    ("beans_poriyal", "Beans poriyal", "Stir-fried French beans with coconut.",
     280, "1 katori (100 g)", 100,
     [((I, "D049"), 250), ((I, "H007"), 25), ((S, "04042"), 10), ((I, "H013"), 2), ((I, "B003"), 5),
      ((I, "G010"), 3), ((S, "02047"), 4)]),
    ("ven_pongal", "Ven pongal", "Rice and moong dal cooked soft with ghee, cumin, ginger and cashews.",
     1000, "1 bowl (200 g)", 200,
     [((I, "A015"), 200), ((I, "B010"), 70), ((S, "01003"), 30), ((I, "G025"), 3), ((I, "G014"), 5),
      ((I, "H005"), 15), ((S, "02047"), 6)]),
    ("rava_upma", "Rava upma", "Semolina cooked with onion, chilli and a mustard, urad dal tempering.",
     500, "1 bowl (200 g)", 200,
     [((I, "A022"), 150), ((I, "G017"), 50), ((S, "04506"), 15), ((I, "H013"), 2), ((I, "B003"), 5),
      ((I, "G008"), 5), ((I, "G010"), 3), ((S, "02047"), 4)]),
    ("coconut_chutney", "Coconut chutney", "Fresh coconut ground with roasted gram, green chilli and ginger, tempered.",
     250, "2 tbsp (30 g)", 30,
     [((I, "H007"), 100), ((I, "B001"), 20), ((I, "G008"), 5), ((I, "G014"), 5), ((S, "02047"), 3),
      ((S, "04042"), 5), ((I, "H013"), 2), ((I, "G010"), 2)]),
    ("egg_curry", "Egg curry", "Boiled eggs simmered in an onion-tomato masala.",
     700, "1 egg with gravy (175 g)", 175,
     [((S, "01129"), 200), ((I, "G017"), 150), ((I, "D076"), 150), ((S, "04042"), 25), ((I, "G014"), 8),
      ((I, "G011"), 8), ((I, "G022"), 6), ((I, "G033"), 2), ((S, "02047"), 6)]),
    ("filter_coffee", "Filter coffee with milk and sugar", "South Indian decoction coffee with whole milk and sugar.",
     150, "1 tumbler (150 g)", 150,
     [((S, "14209"), 50), ((S, "01077"), 100), ((S, "19335"), 8)]),
    ("masala_tea", "Tea with milk and sugar", "Black tea boiled with whole milk and sugar.",
     150, "1 cup (150 g)", 150,
     [((S, "14355"), 75), ((S, "01077"), 75), ((S, "19335"), 8)]),
]
# Sweeteners: when used as an ingredient all of their sugar is added sugar.
ADDED_SUGAR_FOODS = {(S, "19335"), (S, "19296"), (I, "I001")}
# Processed SR28 foods whose added-sugar content is not reported, so it stays unknown.
SR28_UNKNOWN_ADDED_SUGAR = {"18069", "16098"}


def add_added_sugar(nutrients: dict, key) -> None:
    """Single-ingredient foods have no added sugar by definition; sweeteners are all added sugar."""
    if key in ADDED_SUGAR_FOODS:
        if "SUGARS_TOTAL" in nutrients:
            nutrients["SUGARS_ADDED"] = (nutrients["SUGARS_TOTAL"][0], "DERIVED")
    elif "SUGARS_ADDED" not in nutrients:
        nutrients["SUGARS_ADDED"] = (Decimal(0), "DERIVED")


def build_v2() -> str:
    lines = ["-- Generated by tools/seed/generate_seed.py. Do not edit by hand.",
             "-- Nutrient definitions and NASEM DRI / DGA reference values.", ""]
    lines.append("INSERT INTO nutrient_definitions (code, name, short_name, unit, category, display_order, form_note, "
                 "usda_nutrient_id, usda_nutrient_number) VALUES")
    vals = []
    for i, (code, name, short, unit, cat, note, fdc, num) in enumerate(NUTRIENTS, start=1):
        vals.append(f"  ({sql_str(code)}, {sql_str(name)}, {sql_str(short)}, {sql_str(unit)}, {sql_str(cat)}, {i * 10}, "
                    f"{sql_str(note)}, {sql_num(fdc)}, {sql_str(num)})")
    lines.append(",\n".join(vals) + ";\n")

    rows = []
    for code, entries in DRI.items():
        for vtype, male, female, applies, note in entries:
            for sex, values in (("MALE", male), ("FEMALE", female)):
                for (amin, amax), amount in zip(BANDS, values):
                    rows.append((code, "NASEM_DRI", sex, amin, amax, vtype, Decimal(str(amount)), applies,
                                 DRI_SOURCE, DRI_URL, DRI_VERSION, note))
    for code, (pct, note) in DGA_LIMITS.items():
        rows.append((code, "NASEM_DRI", "ANY", 14, 120, "LIMIT_PCT_ENERGY", Decimal(pct), "TOTAL_INTAKE",
                     DGA_SOURCE, DGA_URL, DGA_VERSION, note))
    lines.append("INSERT INTO nutrient_reference_values (nutrient_id, reference_system, sex, age_min, age_max, value_type, "
                 "amount, applies_to, source_name, source_url, source_version, notes)")
    sel = []
    for (code, system, sex, amin, amax, vtype, amount, applies, src, url, ver, note) in rows:
        sel.append(f"SELECT id, {sql_str(system)}, {sql_str(sex)}, {amin}, {amax}, {sql_str(vtype)}, {amount}, "
                   f"{sql_str(applies)}, {sql_str(src)}, {sql_str(url)}, {sql_str(ver)}, {sql_str(note)} "
                   f"FROM nutrient_definitions WHERE code = {sql_str(code)}")
    lines.append("\nUNION ALL ".join(sel) + ";\n")
    return "\n".join(lines)


def food_insert(source, ref, name, category, ftype, prep, local_names, edible, yield_factor, yield_note, notes):
    return (f"INSERT INTO foods (name, category, food_type, preparation_state, basis_quantity, basis_unit, source_id, "
            f"source_food_ref, data_quality, local_names, edible_portion_pct, cooked_yield_factor, cooked_yield_note, notes) "
            f"SELECT {sql_str(name)}, {sql_str(category)}, {sql_str(ftype)}, {sql_str(prep)}, 100, 'G', id, {sql_str(ref)}, "
            f"'VERIFIED', {sql_str(local_names)}, {sql_num(edible)}, {sql_num(yield_factor)}, {sql_str(yield_note)}, "
            f"{sql_str(notes)} FROM food_data_sources WHERE code = {sql_str(source)};")


def nutrient_inserts(source, ref, nutrients):
    if not nutrients:
        return []
    parts = []
    for code, (amount, origin) in sorted(nutrients.items()):
        parts.append(f"({sql_str(code)}, {q(amount)}, {sql_str(origin)})")
    return [
        "INSERT INTO food_nutrients (food_id, nutrient_id, amount, value_origin) "
        "SELECT f.id, n.id, v.amount, v.origin FROM (" +
        " UNION ALL ".join(
            f"SELECT {sql_str(c)} AS code, {q(a)} AS amount, {sql_str(o)} AS origin" for c, (a, o) in sorted(nutrients.items())
        ) +
        f") v JOIN nutrient_definitions n ON n.code = v.code "
        f"JOIN foods f ON f.source_food_ref = {sql_str(ref)} "
        f"JOIN food_data_sources s ON s.id = f.source_id AND s.code = {sql_str(source)};"
    ]


def portion_insert(source, ref, label, unit, qty, grams, is_default, note):
    return (f"INSERT INTO food_portions (food_id, label, unit, unit_quantity, basis_amount, is_default, source_note) "
            f"SELECT f.id, {sql_str(label)}, {sql_str(unit)}, {q(Decimal(qty), '0.001')}, {q(Decimal(grams), '0.001')}, "
            f"{'TRUE' if is_default else 'FALSE'}, {sql_str(note)} FROM foods f JOIN food_data_sources s ON s.id = f.source_id "
            f"WHERE s.code = {sql_str(source)} AND f.source_food_ref = {sql_str(ref)};")


def build_v3(ifct_dir: Path, sr_dir: Path) -> str:
    out = ["-- Generated by tools/seed/generate_seed.py. Do not edit by hand.",
           "-- Food data sources and seed foods from IFCT 2017 and USDA SR28. All values per 100 g edible portion.", ""]
    out.append("""INSERT INTO food_data_sources (code, name, url, version, license_note, default_quality) VALUES
  ('IFCT_2017', 'Indian Food Composition Tables 2017, ICMR-National Institute of Nutrition', 'https://www.nin.res.in/', '2017',
   'Values read from the IFCT 2017 open dataset (npm package ifct2017 v2.1.2). Raw foods, per 100 g edible portion. Not-analysed values are treated as unknown.', 'VERIFIED'),
  ('USDA_SR28', 'USDA National Nutrient Database for Standard Reference, Legacy Release 28', 'https://fdc.nal.usda.gov/', 'SR28 (2016), SR Legacy',
   'Public domain (USDA Agricultural Research Service). Read from the SR28 ABBREV file; source_food_ref is the NDB number.', 'VERIFIED'),
  ('USDA_FDC', 'USDA FoodData Central API', 'https://fdc.nal.usda.gov/api-guide', 'v1',
   'CC0 1.0 public domain dedication. Imported on demand when a user adds a FoodData Central result.', 'EXTERNAL'),
  ('OPEN_FOOD_FACTS', 'Open Food Facts', 'https://world.openfoodfacts.org/', 'API v2',
   'Open Database License (ODbL). Crowd-sourced label data; values are as printed on packaging.', 'EXTERNAL'),
  ('USER', 'Entered by a NutriTrack user', NULL, NULL, 'Private to the user who entered it.', 'USER_ENTERED'),
  ('NUTRITRACK_RECIPE', 'Calculated by NutriTrack from recipe ingredients', NULL, NULL,
   'Sum of ingredient nutrients divided by cooked weight. Cooking losses of heat-sensitive vitamins are not modelled.', 'ESTIMATED');
""")

    ifct_rows = {r["code"]: r for r in csv.DictReader(open(ifct_dir / "compositions" / "index.csv", encoding="utf-8"))}
    abbrev, sr_names = read_sr28(sr_dir)

    def sr_yield(raw_ndb):
        cooked_ndb, method = SR28_YIELD_PAIRS[raw_ndb]
        wr, wc = dec(abbrev[raw_ndb]["Water"]), dec(abbrev[cooked_ndb]["Water"])
        factor = (Decimal(100) - wr) / (Decimal(100) - wc)
        note = (f"Estimated from USDA SR28 moisture of raw NDB {raw_ndb} ({wr} g) and {method} NDB {cooked_ndb} "
                f"({wc} g), assuming solids are retained")
        return factor.quantize(Decimal("0.001")), note

    out.append("-- IFCT 2017 foods")
    for code, (name, cat) in IFCT_FOODS.items():
        r = ifct_rows[code]
        nutrients = ifct_nutrients(r)
        if "ENERGY_KCAL" not in nutrients:
            raise SystemExit(f"IFCT {code} has no energy value; pick another food")
        yf, yn = (None, None)
        if code in IFCT_BORROWED_YIELDS:
            yf, yn = sr_yield(IFCT_BORROWED_YIELDS[code])
            yn = yn + "; borrowed for this IFCT food"
        prep = "RAW"
        if r["grup"] in ("Nuts and Oil Seeds", "Condiments and Spices", "Sugars") or code in ("A011", "A012", "A022", "A023", "A019", "A018", "G033", "E064", "L003", "M007", "M004", "K002", "L001", "L002"):
            prep = "NOT_APPLICABLE"
        if code in ("M004", "M007"):
            prep = "COOKED"
        if r["grup"] in ("Cereals and Millets", "Grain Legumes") and code not in ("A011", "A012", "A019", "A018", "A022", "A023"):
            prep = "DRY"
        if code == "A015" or code == "A014" or code == "A013":
            prep = "RAW"
        add_added_sugar(nutrients, (I, code))
        local = r.get("lang") or None
        out.append(food_insert("IFCT_2017", code, name or r["name"], cat or r["grup"], "GENERIC", prep, local, None, yf, yn,
                               f"IFCT 2017 food {code}: {r['name']}"))
        out.extend(nutrient_inserts("IFCT_2017", code, nutrients))
        for label, unit, grams in TYPICAL_PORTIONS.get(code, []):
            out.append(portion_insert("IFCT_2017", code, label, unit, 1, grams, True, TYPICAL_PORTION_NOTE))

    out.append("\n-- USDA SR28 foods")
    oils = {"04042", "04058", "04047", "04506", "04044", "04583", "04037", "04053", "01003"}
    for ndb, (name, cat, prep) in SR28_FOODS.items():
        row = abbrev[ndb]
        nutrients = sr_nutrients(row)
        if ndb not in SR28_UNKNOWN_ADDED_SUGAR:
            add_added_sugar(nutrients, (S, ndb))
        yf, yn = (sr_yield(ndb) if ndb in SR28_YIELD_PAIRS else (None, None))
        refuse = dec(row["Refuse_Pct"])
        edible = (Decimal(100) - refuse) if refuse else None
        out.append(food_insert("USDA_SR28", ndb, name, cat, "GENERIC", prep, None, edible, yf, yn,
                               f"USDA SR28 NDB {ndb}: {sr_names[ndb]}"))
        out.extend(nutrient_inserts("USDA_SR28", ndb, nutrients))
        portions = sr_portions(row)
        if ndb in oils and not any(p[1] == "TBSP" for p in portions):
            portions.append(("1 tbsp", "TBSP", Decimal(1), Decimal("13.6")))
        for i, (label, unit, qty, grams) in enumerate(portions):
            out.append(portion_insert("USDA_SR28", ndb, label, unit, qty, grams, i == 0, "USDA SR28 household measure"
                                      if (label, unit) != ("1 tbsp", "TBSP") or ndb not in oils else "Typical tablespoon of oil (13.6 g)"))

    out.append("""
-- Cache per-food completeness: share of tracked nutrients with a known value.
UPDATE foods f
SET completeness_pct = ROUND(100 * (SELECT COUNT(*) FROM food_nutrients fn WHERE fn.food_id = f.id)
                             / (SELECT COUNT(*) FROM nutrient_definitions), 2);
""")
    return "\n".join(out)


def build_v4() -> str:
    out = ["-- Generated by tools/seed/generate_seed.py. Do not edit by hand.",
           "-- Built-in South Indian recipes. Only ingredient weights are stored here; the backend",
           "-- (SystemRecipeInitializer) calculates and stores their nutrients with the recipe engine.", ""]
    for key, name, desc, cooked, serving_label, serving_g, ingredients in RECIPES:
        servings = (Decimal(cooked) / Decimal(serving_g)).quantize(Decimal("0.01"))
        out.append(f"INSERT INTO recipes (owner_user_id, is_system, system_key, name, description, servings, "
                   f"total_cooked_weight_g, serving_label) VALUES (NULL, TRUE, {sql_str(key)}, {sql_str(name)}, "
                   f"{sql_str(desc)}, {servings}, {cooked}, {sql_str(serving_label)});")
        for i, ((src, ref), grams) in enumerate(ingredients):
            out.append(f"INSERT INTO recipe_ingredients (recipe_id, food_id, quantity, unit, weight_state, basis_amount, sort_order) "
                       f"SELECT r.id, f.id, {grams}, 'G', 'AS_DEFINED', {grams}, {i} FROM recipes r "
                       f"JOIN food_data_sources s ON s.code = {sql_str(src)} "
                       f"JOIN foods f ON f.source_id = s.id AND f.source_food_ref = {sql_str(ref)} "
                       f"WHERE r.system_key = {sql_str(key)};")
        out.append("")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ifct", required=True, type=Path)
    ap.add_argument("--sr28", required=True, type=Path)
    args = ap.parse_args()
    (MIGRATIONS / "V2__nutrients_and_references.sql").write_text(build_v2(), encoding="utf-8")
    (MIGRATIONS / "V3__seed_foods.sql").write_text(build_v3(args.ifct, args.sr28), encoding="utf-8")
    (MIGRATIONS / "V4__system_recipes.sql").write_text(build_v4(), encoding="utf-8")
    print("wrote V2, V3, V4 to", MIGRATIONS)


if __name__ == "__main__":
    main()
