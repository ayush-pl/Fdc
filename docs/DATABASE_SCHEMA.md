# Database schema

Room database `fdc_nutrition.db`, **schema version 2** (exported JSON: `core/data/schemas/`). Generated from the exported schema; regenerate after schema changes.

## Design notes

- **IDs**: user records use random UUID strings; seeded foods use stable ids (`usda-sr-<FDC id>`, `usda-fndds-<FDC id>`, `est-<key>`) so seed upgrades update rows in place.
- **Timestamps** are UTC epoch milliseconds; calendar **dates** are local epoch days (`LocalDate.toEpochDay()`).
- **Nutrients** are 23 nullable columns (`energy_kcal` … `folate_ug`). `NULL` = unknown, never zero. They are embedded with prefixes: `food_nutrients` (per 100 g/ml), `diary_entries.s_*` (snapshot per reference amount), `p_*` (plans/templates), `t_*` (targets).
- **Historical snapshots**: a diary entry stores the nutrients per reference amount at logging time plus quantity/unit/amount-per-unit. Editing a food or recipe never changes history; quantity edits recalculate from the snapshot; an explicit *correction* action re-snapshots.
- **Referential integrity**: `diary_entries`, `recipe_ingredients` reference `foods`/`recipes` with `ON DELETE RESTRICT`, so referenced foods can only be archived (`is_archived`).
- **Search**: `foods_fts` is an FTS4 external-content index over name, brand and aliases (e.g. *dahi*, *chawal*, *atta*).
- **Targets** are versioned by `effective_from`, so past days are analysed against the target in force then.
- **Planned meals** live in `planned_meals`, separate from `diary_entries`; logging a plan creates a diary entry with a unique `planned_source_key` (idempotent).
- **Sync**: `sync_queue` (coalesced outbox), `sync_record_versions` (last seen server version), `sync_conflicts`, `sync_metadata`.
- **Migrations**: `Migrations.kt` (1→2 adds `index_diary_entries_logged_at`); destructive fallback is disabled. `MigrationTest` builds a real v1 database from `1.json` and opens it with the current code.

## Tables

### `food_categories`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `emoji` | TEXT | no |  |
| `sort_order` | INTEGER | no |  |

### `foods`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `category_id` | TEXT | no |  |
| `basis` | TEXT | no |  |
| `density_g_per_ml` | REAL | yes |  |
| `source` | TEXT | no |  |
| `source_ref` | TEXT | yes |  |
| `data_quality` | TEXT | no |  |
| `preparation` | TEXT | no |  |
| `is_custom` | INTEGER | no |  |
| `is_archived` | INTEGER | no |  |
| `is_popular` | INTEGER | no |  |
| `diet_class` | TEXT | no |  |
| `allergens` | TEXT | no |  |
| `allergen_info_known` | INTEGER | no |  |
| `aliases` | TEXT | yes |  |
| `original_name` | TEXT | yes |  |
| `notes` | TEXT | yes |  |
| `seed_version` | INTEGER | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |
| `source_updated_at` | INTEGER | yes |  |

FK: (category_id) → `food_categories`(id) on delete NO ACTION

Index: `index_foods_name` (name)

Index: `index_foods_category_id` (category_id)

Index: `index_foods_is_archived` (is_archived)

Index: `index_foods_is_popular` (is_popular)

Index: `index_foods_source_source_ref` (source, source_ref)

Index: `index_foods_brand` (brand)

### `foods_fts`

| Column | Type | Null | Notes |
|---|---|---|---|
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `aliases` | TEXT | yes |  |

### `food_nutrients`

| Column | Type | Null | Notes |
|---|---|---|---|
| `food_id` | TEXT | no | PK |
| `energy_kcal` | REAL | yes |  |
| `protein_g` | REAL | yes |  |
| `carbohydrate_g` | REAL | yes |  |
| `fat_g` | REAL | yes |  |
| `saturated_fat_g` | REAL | yes |  |
| `trans_fat_g` | REAL | yes |  |
| `fibre_g` | REAL | yes |  |
| `sugars_g` | REAL | yes |  |
| `added_sugars_g` | REAL | yes |  |
| `cholesterol_mg` | REAL | yes |  |
| `sodium_mg` | REAL | yes |  |
| `potassium_mg` | REAL | yes |  |
| `calcium_mg` | REAL | yes |  |
| `iron_mg` | REAL | yes |  |
| `magnesium_mg` | REAL | yes |  |
| `zinc_mg` | REAL | yes |  |
| `vitamin_a_ug` | REAL | yes |  |
| `vitamin_c_mg` | REAL | yes |  |
| `vitamin_d_ug` | REAL | yes |  |
| `vitamin_e_mg` | REAL | yes |  |
| `vitamin_k_ug` | REAL | yes |  |
| `vitamin_b12_ug` | REAL | yes |  |
| `folate_ug` | REAL | yes |  |

FK: (food_id) → `foods`(id) on delete CASCADE

### `food_servings`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `food_id` | TEXT | no |  |
| `unit` | TEXT | no |  |
| `label` | TEXT | no |  |
| `amount` | REAL | no |  |
| `is_default` | INTEGER | no |  |
| `portion_kind` | TEXT | no |  |
| `source` | TEXT | yes |  |
| `sort_order` | INTEGER | no |  |

FK: (food_id) → `foods`(id) on delete CASCADE

Index: `index_food_servings_food_id` (food_id)

### `food_barcodes`

| Column | Type | Null | Notes |
|---|---|---|---|
| `barcode` | TEXT | no | PK |
| `food_id` | TEXT | no |  |
| `created_at` | INTEGER | no |  |

FK: (food_id) → `foods`(id) on delete CASCADE

Index: `index_food_barcodes_food_id` (food_id)

### `food_favourites`

| Column | Type | Null | Notes |
|---|---|---|---|
| `food_id` | TEXT | no | PK |
| `created_at` | INTEGER | no |  |

FK: (food_id) → `foods`(id) on delete CASCADE

### `search_history`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | INTEGER | no | PK |
| `query` | TEXT | no |  |
| `last_used_at` | INTEGER | no |  |

Index: `index_search_history_query` (query) UNIQUE

### `meals`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `sort_order` | INTEGER | no |  |
| `is_default` | INTEGER | no |  |
| `is_archived` | INTEGER | no |  |

### `diary_entries`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `date` | INTEGER | no |  |
| `meal_id` | TEXT | no |  |
| `type` | TEXT | no |  |
| `food_id` | TEXT | yes |  |
| `recipe_id` | TEXT | yes |  |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `basis` | TEXT | no |  |
| `reference_amount` | REAL | no |  |
| `data_quality` | TEXT | no |  |
| `preparation` | TEXT | no |  |
| `note` | TEXT | yes |  |
| `planned_source_key` | TEXT | yes |  |
| `logged_at` | INTEGER | no |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |
| `s_energy_kcal` | REAL | yes |  |
| `s_protein_g` | REAL | yes |  |
| `s_carbohydrate_g` | REAL | yes |  |
| `s_fat_g` | REAL | yes |  |
| `s_saturated_fat_g` | REAL | yes |  |
| `s_trans_fat_g` | REAL | yes |  |
| `s_fibre_g` | REAL | yes |  |
| `s_sugars_g` | REAL | yes |  |
| `s_added_sugars_g` | REAL | yes |  |
| `s_cholesterol_mg` | REAL | yes |  |
| `s_sodium_mg` | REAL | yes |  |
| `s_potassium_mg` | REAL | yes |  |
| `s_calcium_mg` | REAL | yes |  |
| `s_iron_mg` | REAL | yes |  |
| `s_magnesium_mg` | REAL | yes |  |
| `s_zinc_mg` | REAL | yes |  |
| `s_vitamin_a_ug` | REAL | yes |  |
| `s_vitamin_c_mg` | REAL | yes |  |
| `s_vitamin_d_ug` | REAL | yes |  |
| `s_vitamin_e_mg` | REAL | yes |  |
| `s_vitamin_k_ug` | REAL | yes |  |
| `s_vitamin_b12_ug` | REAL | yes |  |
| `s_folate_ug` | REAL | yes |  |

FK: (meal_id) → `meals`(id) on delete RESTRICT

FK: (food_id) → `foods`(id) on delete RESTRICT

FK: (recipe_id) → `recipes`(id) on delete RESTRICT

Index: `index_diary_entries_date` (date)

Index: `index_diary_entries_date_meal_id` (date, meal_id)

Index: `index_diary_entries_food_id` (food_id)

Index: `index_diary_entries_recipe_id` (recipe_id)

Index: `index_diary_entries_meal_id` (meal_id)

Index: `index_diary_entries_logged_at` (logged_at)

Index: `index_diary_entries_planned_source_key` (planned_source_key) UNIQUE

### `meal_notes`

| Column | Type | Null | Notes |
|---|---|---|---|
| `date` | INTEGER | no | PK |
| `meal_id` | TEXT | no | PK |
| `note` | TEXT | no |  |
| `updated_at` | INTEGER | no |  |

### `day_logs`

| Column | Type | Null | Notes |
|---|---|---|---|
| `date` | INTEGER | no | PK |
| `marked_complete` | INTEGER | no |  |
| `note` | TEXT | yes |  |
| `updated_at` | INTEGER | no |  |

### `saved_meals`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `default_meal_id` | TEXT | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |

### `saved_meal_items`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `saved_meal_id` | TEXT | no |  |
| `sort_order` | INTEGER | no |  |
| `type` | TEXT | no |  |
| `food_id` | TEXT | yes |  |
| `recipe_id` | TEXT | yes |  |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `basis` | TEXT | no |  |
| `reference_amount` | REAL | no |  |
| `data_quality` | TEXT | no |  |
| `p_energy_kcal` | REAL | yes |  |
| `p_protein_g` | REAL | yes |  |
| `p_carbohydrate_g` | REAL | yes |  |
| `p_fat_g` | REAL | yes |  |
| `p_saturated_fat_g` | REAL | yes |  |
| `p_trans_fat_g` | REAL | yes |  |
| `p_fibre_g` | REAL | yes |  |
| `p_sugars_g` | REAL | yes |  |
| `p_added_sugars_g` | REAL | yes |  |
| `p_cholesterol_mg` | REAL | yes |  |
| `p_sodium_mg` | REAL | yes |  |
| `p_potassium_mg` | REAL | yes |  |
| `p_calcium_mg` | REAL | yes |  |
| `p_iron_mg` | REAL | yes |  |
| `p_magnesium_mg` | REAL | yes |  |
| `p_zinc_mg` | REAL | yes |  |
| `p_vitamin_a_ug` | REAL | yes |  |
| `p_vitamin_c_mg` | REAL | yes |  |
| `p_vitamin_d_ug` | REAL | yes |  |
| `p_vitamin_e_mg` | REAL | yes |  |
| `p_vitamin_k_ug` | REAL | yes |  |
| `p_vitamin_b12_ug` | REAL | yes |  |
| `p_folate_ug` | REAL | yes |  |

FK: (saved_meal_id) → `saved_meals`(id) on delete CASCADE

Index: `index_saved_meal_items_saved_meal_id` (saved_meal_id)

### `recipes`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `description` | TEXT | yes |  |
| `instructions` | TEXT | yes |  |
| `servings` | REAL | no |  |
| `final_weight_g` | REAL | yes |  |
| `image_uri` | TEXT | yes |  |
| `is_favourite` | INTEGER | no |  |
| `is_archived` | INTEGER | no |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |

Index: `index_recipes_name` (name)

Index: `index_recipes_is_archived` (is_archived)

### `recipe_ingredients`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `recipe_id` | TEXT | no |  |
| `food_id` | TEXT | no |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `sort_order` | INTEGER | no |  |
| `note` | TEXT | yes |  |

FK: (recipe_id) → `recipes`(id) on delete CASCADE

FK: (food_id) → `foods`(id) on delete RESTRICT

Index: `index_recipe_ingredients_recipe_id` (recipe_id)

Index: `index_recipe_ingredients_food_id` (food_id)

### `meal_plans`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `name` | TEXT | no |  |
| `is_favourite` | INTEGER | no |  |
| `notes` | TEXT | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |

### `meal_plan_items`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `plan_id` | TEXT | no |  |
| `day_offset` | INTEGER | no |  |
| `meal_id` | TEXT | no |  |
| `type` | TEXT | no |  |
| `food_id` | TEXT | yes |  |
| `recipe_id` | TEXT | yes |  |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `basis` | TEXT | no |  |
| `reference_amount` | REAL | no |  |
| `data_quality` | TEXT | no |  |
| `p_energy_kcal` | REAL | yes |  |
| `p_protein_g` | REAL | yes |  |
| `p_carbohydrate_g` | REAL | yes |  |
| `p_fat_g` | REAL | yes |  |
| `p_saturated_fat_g` | REAL | yes |  |
| `p_trans_fat_g` | REAL | yes |  |
| `p_fibre_g` | REAL | yes |  |
| `p_sugars_g` | REAL | yes |  |
| `p_added_sugars_g` | REAL | yes |  |
| `p_cholesterol_mg` | REAL | yes |  |
| `p_sodium_mg` | REAL | yes |  |
| `p_potassium_mg` | REAL | yes |  |
| `p_calcium_mg` | REAL | yes |  |
| `p_iron_mg` | REAL | yes |  |
| `p_magnesium_mg` | REAL | yes |  |
| `p_zinc_mg` | REAL | yes |  |
| `p_vitamin_a_ug` | REAL | yes |  |
| `p_vitamin_c_mg` | REAL | yes |  |
| `p_vitamin_d_ug` | REAL | yes |  |
| `p_vitamin_e_mg` | REAL | yes |  |
| `p_vitamin_k_ug` | REAL | yes |  |
| `p_vitamin_b12_ug` | REAL | yes |  |
| `p_folate_ug` | REAL | yes |  |

FK: (plan_id) → `meal_plans`(id) on delete CASCADE

Index: `index_meal_plan_items_plan_id` (plan_id)

### `planned_meals`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `date` | INTEGER | no |  |
| `meal_id` | TEXT | no |  |
| `recurrence_id` | TEXT | yes |  |
| `logged_entry_id` | TEXT | yes |  |
| `is_skipped` | INTEGER | no |  |
| `note` | TEXT | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |
| `type` | TEXT | no |  |
| `food_id` | TEXT | yes |  |
| `recipe_id` | TEXT | yes |  |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `basis` | TEXT | no |  |
| `reference_amount` | REAL | no |  |
| `data_quality` | TEXT | no |  |
| `p_energy_kcal` | REAL | yes |  |
| `p_protein_g` | REAL | yes |  |
| `p_carbohydrate_g` | REAL | yes |  |
| `p_fat_g` | REAL | yes |  |
| `p_saturated_fat_g` | REAL | yes |  |
| `p_trans_fat_g` | REAL | yes |  |
| `p_fibre_g` | REAL | yes |  |
| `p_sugars_g` | REAL | yes |  |
| `p_added_sugars_g` | REAL | yes |  |
| `p_cholesterol_mg` | REAL | yes |  |
| `p_sodium_mg` | REAL | yes |  |
| `p_potassium_mg` | REAL | yes |  |
| `p_calcium_mg` | REAL | yes |  |
| `p_iron_mg` | REAL | yes |  |
| `p_magnesium_mg` | REAL | yes |  |
| `p_zinc_mg` | REAL | yes |  |
| `p_vitamin_a_ug` | REAL | yes |  |
| `p_vitamin_c_mg` | REAL | yes |  |
| `p_vitamin_d_ug` | REAL | yes |  |
| `p_vitamin_e_mg` | REAL | yes |  |
| `p_vitamin_k_ug` | REAL | yes |  |
| `p_vitamin_b12_ug` | REAL | yes |  |
| `p_folate_ug` | REAL | yes |  |

Index: `index_planned_meals_date` (date)

Index: `index_planned_meals_recurrence_id_date` (recurrence_id, date) UNIQUE

Index: `index_planned_meals_logged_entry_id` (logged_entry_id)

### `plan_recurrences`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `meal_id` | TEXT | no |  |
| `days_of_week` | INTEGER | no |  |
| `start_date` | INTEGER | no |  |
| `end_date` | INTEGER | yes |  |
| `is_active` | INTEGER | no |  |
| `created_at` | INTEGER | no |  |
| `type` | TEXT | no |  |
| `food_id` | TEXT | yes |  |
| `recipe_id` | TEXT | yes |  |
| `name` | TEXT | no |  |
| `brand` | TEXT | yes |  |
| `quantity` | REAL | no |  |
| `unit` | TEXT | no |  |
| `unit_label` | TEXT | no |  |
| `amount_per_unit` | REAL | no |  |
| `basis` | TEXT | no |  |
| `reference_amount` | REAL | no |  |
| `data_quality` | TEXT | no |  |
| `p_energy_kcal` | REAL | yes |  |
| `p_protein_g` | REAL | yes |  |
| `p_carbohydrate_g` | REAL | yes |  |
| `p_fat_g` | REAL | yes |  |
| `p_saturated_fat_g` | REAL | yes |  |
| `p_trans_fat_g` | REAL | yes |  |
| `p_fibre_g` | REAL | yes |  |
| `p_sugars_g` | REAL | yes |  |
| `p_added_sugars_g` | REAL | yes |  |
| `p_cholesterol_mg` | REAL | yes |  |
| `p_sodium_mg` | REAL | yes |  |
| `p_potassium_mg` | REAL | yes |  |
| `p_calcium_mg` | REAL | yes |  |
| `p_iron_mg` | REAL | yes |  |
| `p_magnesium_mg` | REAL | yes |  |
| `p_zinc_mg` | REAL | yes |  |
| `p_vitamin_a_ug` | REAL | yes |  |
| `p_vitamin_c_mg` | REAL | yes |  |
| `p_vitamin_d_ug` | REAL | yes |  |
| `p_vitamin_e_mg` | REAL | yes |  |
| `p_vitamin_k_ug` | REAL | yes |  |
| `p_vitamin_b12_ug` | REAL | yes |  |
| `p_folate_ug` | REAL | yes |  |

### `nutrition_targets`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `effective_from` | INTEGER | no |  |
| `water_ml` | REAL | no |  |
| `macro_method` | TEXT | no |  |
| `protein_pct` | REAL | yes |  |
| `carb_pct` | REAL | yes |  |
| `fat_pct` | REAL | yes |  |
| `origin` | TEXT | no |  |
| `created_at` | INTEGER | no |  |
| `t_energy_kcal` | REAL | yes |  |
| `t_protein_g` | REAL | yes |  |
| `t_carbohydrate_g` | REAL | yes |  |
| `t_fat_g` | REAL | yes |  |
| `t_saturated_fat_g` | REAL | yes |  |
| `t_trans_fat_g` | REAL | yes |  |
| `t_fibre_g` | REAL | yes |  |
| `t_sugars_g` | REAL | yes |  |
| `t_added_sugars_g` | REAL | yes |  |
| `t_cholesterol_mg` | REAL | yes |  |
| `t_sodium_mg` | REAL | yes |  |
| `t_potassium_mg` | REAL | yes |  |
| `t_calcium_mg` | REAL | yes |  |
| `t_iron_mg` | REAL | yes |  |
| `t_magnesium_mg` | REAL | yes |  |
| `t_zinc_mg` | REAL | yes |  |
| `t_vitamin_a_ug` | REAL | yes |  |
| `t_vitamin_c_mg` | REAL | yes |  |
| `t_vitamin_d_ug` | REAL | yes |  |
| `t_vitamin_e_mg` | REAL | yes |  |
| `t_vitamin_k_ug` | REAL | yes |  |
| `t_vitamin_b12_ug` | REAL | yes |  |
| `t_folate_ug` | REAL | yes |  |

Index: `index_nutrition_targets_effective_from` (effective_from) UNIQUE

### `water_entries`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `date` | INTEGER | no |  |
| `amount_ml` | REAL | no |  |
| `beverage` | TEXT | no |  |
| `logged_at` | INTEGER | no |  |

Index: `index_water_entries_date` (date)

### `body_measurements`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `date` | INTEGER | no |  |
| `type` | TEXT | no |  |
| `custom_name` | TEXT | yes |  |
| `value` | REAL | no |  |
| `note` | TEXT | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |

Index: `index_body_measurements_date` (date)

Index: `index_body_measurements_type_date` (type, date)

### `user_profile`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `display_name` | TEXT | no |  |
| `date_of_birth` | INTEGER | yes |  |
| `height_cm` | REAL | yes |  |
| `sex` | TEXT | no |  |
| `current_weight_kg` | REAL | yes |  |
| `start_weight_kg` | REAL | yes |  |
| `target_weight_kg` | REAL | yes |  |
| `activity_level` | TEXT | no |  |
| `goal` | TEXT | no |  |
| `weekly_change_kg` | REAL | no |  |
| `dietary_preference` | TEXT | no |  |
| `allergies` | TEXT | no |  |
| `restrictions` | TEXT | yes |  |
| `created_at` | INTEGER | no |  |
| `updated_at` | INTEGER | no |  |

### `user_preferences`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT | no | PK |
| `theme` | TEXT | no |  |
| `weight_unit` | TEXT | no |  |
| `length_unit` | TEXT | no |  |
| `default_solid_unit` | TEXT | no |  |
| `default_liquid_unit` | TEXT | no |  |
| `count_beverages_as_water` | INTEGER | no |  |
| `water_quick_amounts` | TEXT | no |  |
| `dynamic_color` | INTEGER | no |  |
| `sync_enabled` | INTEGER | no |  |
| `supabase_url` | TEXT | yes |  |
| `supabase_anon_key` | TEXT | yes |  |
| `usda_api_key` | TEXT | yes |  |

### `reminders`

| Column | Type | Null | Notes |
|---|---|---|---|
| `type` | TEXT | no | PK |
| `enabled` | INTEGER | no |  |
| `hour` | INTEGER | no |  |
| `minute` | INTEGER | no |  |

### `app_metadata`

| Column | Type | Null | Notes |
|---|---|---|---|
| `key` | TEXT | no | PK |
| `value` | TEXT | no |  |

### `sync_metadata`

| Column | Type | Null | Notes |
|---|---|---|---|
| `key` | TEXT | no | PK |
| `value` | TEXT | no |  |

### `sync_queue`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | INTEGER | no | PK |
| `entity_type` | TEXT | no |  |
| `entity_id` | TEXT | no |  |
| `operation` | TEXT | no |  |
| `base_remote_version` | INTEGER | yes |  |
| `created_at` | INTEGER | no |  |
| `attempts` | INTEGER | no |  |
| `last_error` | TEXT | yes |  |
| `status` | TEXT | no |  |

Index: `index_sync_queue_entity_type_entity_id` (entity_type, entity_id) UNIQUE

Index: `index_sync_queue_status` (status)

### `sync_record_versions`

| Column | Type | Null | Notes |
|---|---|---|---|
| `entity_type` | TEXT | no | PK |
| `entity_id` | TEXT | no | PK |
| `remote_version` | INTEGER | no |  |

### `sync_conflicts`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | INTEGER | no | PK |
| `entity_type` | TEXT | no |  |
| `entity_id` | TEXT | no |  |
| `local_payload` | TEXT | yes |  |
| `remote_payload` | TEXT | yes |  |
| `remote_version` | INTEGER | no |  |
| `detected_at` | INTEGER | no |  |
| `resolved` | INTEGER | no |  |

Index: `index_sync_conflicts_entity_type_entity_id` (entity_type, entity_id)
