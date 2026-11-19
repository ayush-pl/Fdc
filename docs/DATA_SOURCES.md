# Food data sources and licensing

| Source | Used for | Licence | How it's labelled in the app |
|---|---|---|---|
| USDA FoodData Central — **SR Legacy** (April 2018) | ~7,000 single foods: fruits, vegetables, grains, legumes, nuts, oils, dairy, eggs, meat, fish, beverages… raw and cooked forms with USDA portion weights | Public domain (CC0 1.0) | **Verified DB** |
| USDA FoodData Central — **FNDDS 2021–2023** (released 2024-10-31) | ~5,100 foods "as eaten", incl. Indian dishes (roti/chapati, paratha, dal, sambar, idli, dosa, upma, biryani, chicken/vegetable/fish curry, palak paneer, paneer, samosa, pakora, chutney, naan, puri, ladoo, barfi…) with typical portion weights | Public domain (CC0 1.0) | **Estimate** (recipe-based generic values) |
| Home-style estimates (7 foods) | Poha, khichdi, rajma curry, chole masala, besan chilla, dhokla, thepla — not present in USDA | Calculated by this project from SR Legacy ingredients; recipe stored in each food's notes | **Calculated** |
| Open Food Facts (online, on barcode scan) | Packaged products | ODbL 1.0 (database), DbCL (contents) | **Label** — user reviews before saving |
| USDA FDC API (online, optional search) | Additional foods incl. branded | Public domain | Verified DB / Label |
| User | Custom foods, serving sizes, quick-add | — | **Yours** |

## Bundled database
- Built reproducibly by `tools/fooddata/build_seed.py` from the official CSV downloads
  (`https://fdc.nal.usda.gov/download-datasets`). Output: `core/data/src/main/assets/seed/foods.seed`
  (gzip-compressed JSON; the neutral extension stops the Android build from decompressing it).
- 12,153 foods in 22 categories; 93 flagged *popular*; regional aliases for search (e.g. *dahi → curd/yogurt*,
  *chawal → rice*, *atta*, *rava/sooji*, *besan*, *rajma*, *chole*).
- Values are copied **verbatim** per 100 g for 23 nutrients. Missing values stay missing — e.g. FNDDS
  has no trans-fat or added-sugar values, so those show "—" and totals report partial coverage.
- Excluded: baby foods/formula, restaurant-chain and branded SR items, "as ingredient" survey codes, foods without an energy value.
- Each food keeps its FDC ID (shown on the food screen) and publication date (data freshness).
- Raw vs cooked is derived from the USDA description; USDA portion weights are edible-portion weights and
  are labelled as raw/cooked/edible/prepared accordingly.
- Diet class is assigned only where it is reliable (single-ingredient plant foods → vegan; plain dairy →
  vegetarian; meat/fish/egg categories). Mixed dishes are **unknown**. USDA has no allergen data, so allergen
  status is **unknown** for all bundled foods — never "allergen-free".

## Indian food data note
The Indian Food Composition Tables (IFCT 2017, ICMR-NIN) would be the preferred regional source but are not
openly licensed for redistribution, so they are not bundled. Users can add IFCT values as custom foods.
The adapter architecture (`FoodDataProvider`) allows adding a licensed regional source later.

## Conflicting values
Different sources are never merged: each imported product is its own food row with `source`,
`source_ref` and data quality, and duplicate detection reuses an existing row for the same source record/barcode.

## Rate limits / etiquette
- Open Food Facts: requests only on explicit barcode lookups, generic app User-Agent, results cached per session and saved locally once accepted.
- USDA API: min. 1.5 s between requests; `DEMO_KEY` by default, personal key configurable.
- No scraping of any website.
