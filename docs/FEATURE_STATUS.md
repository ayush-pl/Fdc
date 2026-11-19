# Feature status

Legend: ✅ implemented and covered by automated tests · 🟢 implemented, exercised manually/compiles but no dedicated automated test · ⚠️ partial · ❌ not implemented

## Acceptance scenarios
Data-layer tests: `core/data/src/test/.../AcceptanceScenariosTest.kt` (real Room DB seeded with the bundled data).
UI tests: `app/src/test/.../FoodLoggingUiTest.kt` (real app + Hilt graph under Robolectric, screenshots in `docs/screenshots`).

| # | Scenario | Status | Test |
|---|---|---|---|
| 1 | Create a user profile | ✅ | `scenarios_1_to_3…`, UI `onboardingCreatesProfileAndTargets` |
| 2 | Configure calorie target | ✅ | same |
| 3 | Configure P/C/F targets | ✅ | same (percentage consistency + gram-based) |
| 4 | Search for a food | ✅ | `scenarios_4_to_13…`, `SeedAndSearchTest`, UI `searchLogEdit…` |
| 5 | View nutritional information | ✅ | same |
| 6 | Add 150 g cooked rice | ✅ | same (UI enters 150 g) |
| 7 | Verify the calculation | ✅ | 195 kcal asserted in data + UI |
| 8 | Add dal to lunch | ✅ | food-specific cup serving |
| 9 | Foods to breakfast and dinner | ✅ | egg, roti (piece serving) |
| 10 | Verify daily calorie totals | ✅ | |
| 11 | Verify macro totals | ✅ | |
| 12 | Modify a food quantity | ✅ | data + UI (150 → 200 g) |
| 13 | Verify automatic recalculation | ✅ | 260 kcal in UI |
| 14 | Create a custom food | ✅ | `scenarios_14_15…` |
| 15 | Save a favourite food | ✅ | |
| 16 | Multi-ingredient recipe | ✅ | `scenarios_16_to_18…` (paneer bhurji) |
| 17 | Verify recipe nutrition | ✅ | totals, per serving, per 100 g with cooked weight |
| 18 | Log a recipe serving | ✅ | + recipe edits don't change history |
| 19 | Scan a packaged food barcode | ✅ (lookup) / 🟢 (camera) | `scenarios_19_20…` with MockWebServer; CameraX/ML Kit scanning not testable on JVM |
| 20 | Handle an unknown barcode | ✅ | 404 and status 0 → NotFound, manual entry |
| 21 | Record water intake | ✅ | beverages excluded unless enabled |
| 22 | Record body weight | ✅ | |
| 23 | Historical weight progression | ✅ | progress %, weekly averages |
| 24 | Weekly nutrition analytics | ✅ | averages over logged days |
| 25 | Edit a historical entry | ✅ | `scenarios_25_26…` |
| 26 | Verify historical snapshots | ✅ | food edit doesn't change history; explicit correction does |
| 27 | Light/dark theme | ✅ | UI `themeSwitchAppliesDarkAndLight` (pixel luminance check) |
| 28 | Close/reopen without losing records | ✅ | file-backed DB reopened |
| 29 | Use without internet | ✅ | search/log offline; lookups report "unavailable" |
| 30 | Export nutrition records | ✅ | CSV zip contents, escaping, blanks for unknown |
| 31 | Restore without corrupting data | ✅ | merge, no silent overwrite, idempotent, invalid rejected |
| 32 | Missing data ≠ zero | ✅ | quick-add + FNDDS trans fat unknown |

Other tests: 45 domain unit tests (calculations, conversions, recipes, targets, aggregation, snapshots, weight
progression, analytics, diet checks), `MigrationTest` (v1 → v2), `SyncEngineTest` (two devices, conflicts, offline retry).
Total: **70 automated tests, all passing**.

## Features
| Area | Status | Notes |
|---|---|---|
| Home dashboard | 🟢 | ring, macros, water quick add, meals, one-tap re-log, weekly bars, weight |
| Calories burned | ❌ | no verified activity integration (Health Connect) — intentionally hidden |
| Food database (12k, 22 categories) | ✅ | USDA SR Legacy + FNDDS, Indian dishes, raw/cooked |
| Search (FTS, aliases, debounced, history) | ✅ | |
| Category browse, popular, recent, favourites, custom | 🟢 | |
| Compare foods | 🟢 | |
| Custom foods (per-100 or per-serving entry, validation, duplicates) | ✅ | |
| User serving sizes on any food | 🟢 | |
| Archive instead of delete | ✅ | enforced by FK RESTRICT |
| Barcode scanning (CameraX + ML Kit) + manual entry | 🟢 | camera path needs a device |
| Open Food Facts lookup, review, local caching | ✅ | |
| USDA online search | ⚠️ | implemented, parser untested against live API in CI |
| Diary: meals, custom meals, notes, edit/move/duplicate/copy/delete with undo | ✅ / 🟢 | |
| Saved (favourite) meals | 🟢 | |
| Quick calorie logging | ✅ | |
| Photo-assisted logging | ❌ | architecture hook only (`FoodDataProvider` pattern); no recognizer shipped — would require user confirmation |
| Recipes (ingredients, servings, cooked weight, photo, favourite, duplicate, archive) | ✅ | |
| Meal planner (week, copy/move day, duplicate, templates, recurrence, log as eaten) | ✅ | |
| Targets (Mifflin–St Jeor, safety caps, %/g macros, micros, versioned) | ✅ | |
| Nutrition analytics (day view + 7 ranges, 9 charts, coverage) | ✅ (calc) / 🟢 (UI) | |
| Water tracker | ✅ | |
| Body weight & measurements (kg/lb, cm/in, custom) | ✅ | |
| Calendar history (complete/partial/empty/planned) | 🟢 | |
| Profile, diet, allergies, units, theme | ✅ / 🟢 | |
| Reminders (WorkManager, skip if already logged, permission) | 🟢 | not unit-tested |
| JSON backup / validated restore | ✅ | |
| CSV export | ✅ | |
| Delete all data | 🟢 | |
| Supabase auth + sync + conflicts + account deletion | ✅ (engine) / ⚠️ (live) | tested with in-memory backend; not run against a live Supabase project here |
| Accessibility | ⚠️ | content descriptions on icons/charts/rings, semantic merges; no TalkBack audit |

## Known limitations / outstanding
- **No instrumented (device/emulator) tests were run**: this environment has no KVM. UI tests run on the JVM via Robolectric. The release APK (R8-minified) builds but was not launched on a device.
- Regional Indian composition data (IFCT 2017) isn't bundled for licensing reasons; FNDDS values are U.S. recipe-based estimates.
- Micronutrient targets cover adults 19+ only; pregnancy/lactation/children not modelled.
- Recipe photos and device preferences aren't synced.
- Large ranges (12 months) compute analytics in memory — fine for years of personal data, not benchmarked beyond that.
