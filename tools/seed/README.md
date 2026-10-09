# Seed data generator

`generate_seed.py` writes Flyway migrations V2 (nutrients and reference values), V3 (seed foods)
and V4 (built-in South Indian recipes) from two published datasets:

| Dataset | Where the script reads it | Licence |
|---|---|---|
| Indian Food Composition Tables 2017 (ICMR-NIN) | `ifct2017` npm package v2.1.2, `compositions/index.csv` | IFCT © ICMR-NIN; cite IFCT 2017 when reusing values |
| USDA National Nutrient Database for Standard Reference, Release 28 | `fda-nutrient-database` npm package v1.0.2, `data/ABBREV.txt` + `data/FOOD_DES.txt` | Public domain |

To regenerate:

```bash
mkdir -p /tmp/seed && cd /tmp/seed
npm pack ifct2017@2.1.2 fda-nutrient-database@1.0.2
mkdir ifct sr28 && tar xzf ifct2017-2.1.2.tgz -C ifct && tar xzf fda-nutrient-database-1.0.2.tgz -C sr28
cd -   # back to the repository root
python3 tools/seed/generate_seed.py --ifct /tmp/seed/ifct/package --sr28 /tmp/seed/sr28/package
```

Only edit the generated migrations through this script, and only before they have been applied to a
database you care about (Flyway checksums them). For changes after release, add a new migration.

## Rules the script follows

* No nutrient value is typed by hand. Each value is read from a dataset and unit-converted to the
  nutrient's canonical unit.
* The IFCT open dataset encodes "not analysed" as 0, so IFCT zeros are stored as **unknown**. Two
  definitional zeros are added and marked `DERIVED`: cholesterol in plant foods and added sugar in
  single-ingredient foods.
* Derived values are labelled `DERIVED`: IFCT vitamin A as RAE (retinol + β-carotene/12 +
  (α-carotene + β-cryptoxanthin)/24), IFCT vitamin D (D2 + D3), IFCT folate as DFE (unfortified
  food folate), total carbohydrate from IFCT available carbohydrate + fibre, and available
  carbohydrate from SR28 carbohydrate − fibre.
* Cooked-yield factors on raw foods are estimates from a solids mass balance of SR28 raw/cooked
  pairs and are labelled as such in `foods.cooked_yield_note`.
* Recipe dishes (idli, dosa, sambar...) only store ingredient weights. The backend calculates their
  nutrients with the recipe engine at startup and marks them `ESTIMATED`.
* Typical portion weights for IFCT foods (one egg, one banana) are labelled as NutriTrack estimates.
