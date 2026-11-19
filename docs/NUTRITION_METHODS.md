# Nutrition calculation methods

All calculations live in the pure-Kotlin module `:core:domain` (`calc/`), covered by unit tests.

## Nutrient scaling
`consumed = (amount in basis units / reference amount) × value per reference amount`
— reference is 100 g or 100 ml for foods, 1 serving for quick-add and weight-less recipes.
Full `Double` precision is kept; rounding happens only in the UI (`NutrientFormat`).
Example (spec): 150 g of rice at 130 kcal / 2.7 g P / 28 g C / 0.3 g F per 100 g → 195 kcal, 4.05 g, 42 g, 0.45 g.

## Units (`ServingConverter`)
- g, kg, oz, lb: universal for gram-based foods. ml, L, fl oz, cup (236.59 ml), tbsp, tsp: universal **volumes** for ml-based foods.
- Mass ↔ volume only with a known food density (derived for beverages from the USDA "1 cup" weight).
- Pieces, slices, bowls, plates, servings and food-specific cups/spoons come **only** from per-food serving
  definitions (USDA portions or user-defined, e.g. "my katori = 180 g"). No universal bowl/piece weight is assumed.

## Missing data
`Nutrients` is a map where absent = unknown. Aggregation (`NutrientAggregator`) sums known values and reports
`knownCount/itemCount`; a total with no known values is *unknown* (null), never 0. The UI shows "—",
"data for 3/5 items" and coverage in analytics/CSV. Quick-add stores only the values entered.

## Recipes
Total = Σ ingredient contributions. Per serving = total / servings. Per 100 g = total / weight × 100, where
weight = recorded cooked weight if entered, else the sum of ingredient weights (unavailable if an ingredient
has no gram weight). Logging a recipe stores a snapshot; later recipe edits don't change history.

## Energy targets
- BMR: Mifflin–St Jeor (1990): `10·kg + 6.25·cm − 5·age + s`, s = +5 (male), −161 (female), −78 (prefer not to say: midpoint).
- TDEE = BMR × activity factor (1.2 / 1.375 / 1.55 / 1.725 / 1.9).
- Goal: ±(kg per week × 7,700 kcal / 7). Safety: loss ≤ 1 kg/wk, deficit ≤ 25 % of TDEE, target ≥ max(BMR, 1,200 F / 1,500 M / 1,350 unspecified); gain ≤ 0.5 kg/wk and ≤ +500 kcal. Rounded to 10 kcal. Warnings are shown when caps apply.
- Macros: percentage split (default 25/50/25 % P/C/F, within AMDR) → grams via 4/4/9 kcal/g, so % targets always match calories; gram-based targets show their implied kcal and the difference.
- Defaults (adults): fibre 14 g/1,000 kcal; added sugars and saturated fat < 10 % energy; sodium 2,300 mg (DGA 2020–2025); vitamins/minerals NIH ODS RDA/AI by sex and age band; water 2.0 L (F) / 2.5 L (M) (EFSA). Every value is user-editable.
These are population estimates and **not medical advice**.

## Analytics
Averages use **logged days only**. Calorie consistency = share of logged days within ±10 % of that day's
target. Target completion: GOAL nutrients ≥ 100 %, LIMIT nutrients ≤ 100 %, calories/macros within ±10 %.
Targets are versioned, so each day is compared with the target that applied then.
Weight: start (profile or first record), current (latest), change, progress % toward target (clamped 0–100),
Monday-based weekly averages and week-over-week trend; the UI discourages over-reading daily fluctuations.
Day status: *complete* if marked complete or ≥ 3 meals logged, *partial* if any entry, else *empty*.
