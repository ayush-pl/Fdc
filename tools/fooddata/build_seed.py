#!/usr/bin/env python3
"""
Builds the bundled food database asset (app/src/main/assets/seed/foods.json.gz) from USDA
FoodData Central CSV downloads (public domain, CC0 1.0).

Usage:
  python3 -I tools/fooddata/build_seed.py <fdc_download_dir> <output.json.gz>

<fdc_download_dir> must contain the extracted folders:
  FoodData_Central_sr_legacy_food_csv_2018-04/
  FoodData_Central_survey_food_csv_2024-10-31/
Download from https://fdc.nal.usda.gov/download-datasets

Nutrient values are copied verbatim (per 100 g). Missing values stay missing (null), never zero.
"Home-style estimate" foods are calculated from documented standard recipes of USDA ingredients and
are labelled as estimates (data quality CALCULATED) in the app.
"""
import csv, gzip, json, os, re, sys
from collections import defaultdict
from fractions import Fraction

csv.field_size_limit(10**9)
SEED_VERSION = 1

# App nutrient key -> USDA nutrient id
NUTRIENTS = [
    ("energy_kcal", 1008), ("protein_g", 1003), ("carbohydrate_g", 1005), ("fat_g", 1004),
    ("saturated_fat_g", 1258), ("trans_fat_g", 1257), ("fibre_g", 1079), ("sugars_g", 2000),
    ("added_sugars_g", 1235), ("cholesterol_mg", 1253), ("sodium_mg", 1093), ("potassium_mg", 1092),
    ("calcium_mg", 1087), ("iron_mg", 1089), ("magnesium_mg", 1090), ("zinc_mg", 1095),
    ("vitamin_a_ug", 1106), ("vitamin_c_mg", 1162), ("vitamin_d_ug", 1114), ("vitamin_e_mg", 1109),
    ("vitamin_k_ug", 1185), ("vitamin_b12_ug", 1178), ("folate_ug", 1190),
]
NID = {nid: i for i, (_, nid) in enumerate(NUTRIENTS)}
# FNDDS food_nutrient.csv references legacy nutrient numbers (e.g. 208 = energy) instead of ids.
NBR = {1008: 208, 1003: 203, 1005: 205, 1004: 204, 1258: 606, 1257: 605, 1079: 291, 2000: 269, 1235: 539,
       1253: 601, 1093: 307, 1092: 306, 1087: 301, 1089: 303, 1090: 304, 1095: 309, 1106: 320, 1162: 401,
       1114: 328, 1109: 323, 1185: 430, 1178: 418, 1190: 435}
for _nid, _nbr in NBR.items():
    NID[_nbr] = NID[_nid]

CATEGORIES = [
    ("indian", "Indian foods", "🍛"), ("fruits", "Fruits", "🍎"), ("vegetables", "Vegetables", "🥦"),
    ("grains", "Grains & cereals", "🌾"), ("rice", "Rice & rice products", "🍚"), ("bread", "Bread & bakery", "🍞"),
    ("dairy", "Dairy products", "🧀"), ("milk", "Milk & milk alternatives", "🥛"), ("eggs", "Eggs", "🥚"),
    ("meat", "Meat & poultry", "🍗"), ("fish", "Fish & seafood", "🐟"), ("legumes", "Pulses & legumes", "🫘"),
    ("nuts", "Nuts & seeds", "🥜"), ("oils", "Oils & fats", "🫒"), ("beverages", "Beverages", "🥤"),
    ("snacks", "Snacks", "🍿"), ("sweets", "Sweets & desserts", "🍰"), ("fast_food", "Fast food", "🍔"),
    ("international", "International dishes", "🌍"), ("condiments", "Spices, sauces & condiments", "🧂"),
    ("packaged", "Packaged foods", "📦"), ("homemade", "Homemade foods", "🏠"),
]

INDIAN_KW = re.compile(r"\b(dal|roti|chappatti|chapati|paratha|biryani|idli|dosa|upma|sambar|paneer|samosa|pakora|chutney|ladoo|ghee|naan|tikka|masala|korma|tandoori|palak|channa|chana|raita|lassi|kheer|gulab|puri|khichdi|vindaloo|saag|aloo|curry|pulao|halwa|jalebi|barfi)\b", re.I)
COOKED_KW = re.compile(r"\b(cooked|boiled|baked|roasted|fried|grilled|steamed|braised|broiled|stewed|toasted|prepared|scrambled|poached|sauteed|microwaved|simmered|stir-fried|rotisserie)\b", re.I)
MEAT_KW = re.compile(r"\b(chicken|beef|pork|lamb|mutton|goat|meat|turkey|ham|bacon|sausage|veal|duck|venison|pepperoni|salami|bologna|frankfurter|hot dog|liver|meatball|gelatin)\b", re.I)
FISH_KW = re.compile(r"\b(fish|tuna|salmon|shrimp|prawn|crab|lobster|cod|sardine|anchov|clam|oyster|mussel|scallop|squid|seafood|tilapia|mackerel|trout|catfish|herring|sushi)\b", re.I)
EGG_KW = re.compile(r"\begg", re.I)
DAIRY_KW = re.compile(r"\b(milk|cheese|butter|cream|yogurt|ghee|paneer|whey|curd|lassi|raita|ice cream|custard)\b", re.I)


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        yield from csv.DictReader(f)


def load_dataset(root, sub):
    d = os.path.join(root, sub, sub)
    foods = {r["fdc_id"]: r for r in read_csv(os.path.join(d, "food.csv"))}
    values = defaultdict(lambda: [None] * len(NUTRIENTS))
    for r in read_csv(os.path.join(d, "food_nutrient.csv")):
        nid = int(r["nutrient_id"]) if r["nutrient_id"].isdigit() else -1
        if nid in NID and r["fdc_id"] in foods and r["amount"] != "":
            values[r["fdc_id"]][NID[nid]] = float(r["amount"])
    portions = defaultdict(list)
    for r in read_csv(os.path.join(d, "food_portion.csv")):
        if r["fdc_id"] in foods:
            portions[r["fdc_id"]].append(r)
    return d, foods, values, portions


def parse_amount(s):
    s = s.strip()
    m = re.match(r"^(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(.*)$", s)
    if not m:
        return None, s
    num = m.group(1)
    try:
        if " " in num:
            a, b = num.split()
            v = float(a) + float(Fraction(b))
        else:
            v = float(Fraction(num))
    except (ValueError, ZeroDivisionError):
        return None, s
    return v, m.group(2).strip()


def unit_for(text):
    t = text.lower().strip()
    if re.match(r"^(cup|cups)\b", t): return "CUP"
    if re.match(r"^(tbsp|tablespoon)", t): return "TABLESPOON"
    if re.match(r"^(tsp|teaspoon)", t): return "TEASPOON"
    if re.match(r"^fl oz", t): return "FLUID_OUNCE"
    if re.match(r"^(oz|ounce|lb|pound|g\b|gram|kg)", t): return None  # plain mass units are universal already
    if re.match(r"^(ml|liter|litre|quart|pint|gallon|cubic)", t): return None
    if "slice" in t: return "SLICE"
    if "bowl" in t: return "BOWL"
    if re.match(r"^(serving|portion)", t): return "SERVING"
    if t.startswith("quantity not specified"): return "SERVING"
    return "PIECE"


def portions_for(rows, is_survey):
    out, seen = [], set()
    for r in sorted(rows, key=lambda r: int(r["seq_num"] or 0)):
        try:
            g = float(r["gram_weight"])
        except ValueError:
            continue
        if g <= 0:
            continue
        if is_survey:
            desc = r["portion_description"].strip()
            if desc.lower().startswith("quantity not specified"):
                unit, label, per = "SERVING", "Typical serving (USDA)", g
            else:
                amt, rest = parse_amount(desc)
                if amt is None or amt <= 0:
                    amt, rest = 1.0, desc
                unit = unit_for(rest)
                if unit is None:
                    continue
                per = g / amt
                label = "1 " + rest if amt != 1 else desc
        else:
            try:
                amt = float(r["amount"] or 1)
            except ValueError:
                amt = 1.0
            mod = r["modifier"].strip() or r["portion_description"].strip()
            if not mod or amt <= 0:
                continue
            unit = unit_for(mod)
            if unit is None:
                continue
            per = g / amt
            label = f"1 {mod}"
        label = re.sub(r"\s+", " ", label)[:80]
        key = (unit, label.lower())
        if key in seen:
            continue
        seen.add(key)
        out.append([unit, label, round(per, 3)])
    return out[:8]


def sr_category(cat_id, desc):
    d = desc.lower()
    m = {
        "1": "dairy", "4": "oils", "5": "meat", "7": "meat", "10": "meat", "13": "meat", "17": "meat",
        "6": "condiments", "2": "condiments", "8": "grains", "9": "fruits", "11": "vegetables", "12": "nuts",
        "14": "beverages", "28": "beverages", "15": "fish", "16": "legumes", "18": "bread", "19": "sweets",
        "20": "grains", "22": "international", "23": "snacks", "21": "fast_food",
    }.get(cat_id)
    if m is None:
        return None
    if cat_id == "1":
        if d.startswith("egg"): return "eggs"
        if d.startswith("milk") or "buttermilk" in d: return "milk"
    if cat_id == "9" and "juice" in d: return "beverages"
    if cat_id == "14" and re.search(r"soymilk|almond milk|rice milk|oat milk|milk substitute", d): return "milk"
    if cat_id == "20" and d.startswith("rice"): return "rice"
    if cat_id == "18" and re.search(r"cake|cookie|pie|brownie|doughnut|danish|pastry|sweet roll|strudel|eclair", d): return "sweets"
    return m


def wweia_category(c):
    c = int(c)
    if c in (1002, 1004, 1006, 1008, 1202, 1204, 1206, 1208, 1902): return "milk"
    if c == 1402: return "beverages"
    if 1600 <= c < 2000: return "dairy"
    if 2000 <= c < 2400 or 2600 <= c < 2700: return "meat"
    if 2400 <= c < 2500: return "fish"
    if 2500 <= c < 2600: return "eggs"
    if c in (2802, 2806): return "legumes"
    if c == 2804: return "nuts"
    if c == 3202 or c in (4002, 3402): return "rice"
    if 3000 <= c < 3600 or 3800 <= c < 3900: return "international"
    if 3600 <= c < 3800: return "fast_food"
    if c == 4004 or 4600 <= c < 4900: return "grains"
    if 4200 <= c < 4500: return "bread"
    if 5000 <= c < 5500: return "snacks"
    if 5500 <= c < 5900: return "sweets"
    if 6000 <= c < 6100: return "fruits"
    if 6400 <= c < 6900: return "fast_food" if c == 6804 else "vegetables"
    if 7000 <= c < 7900 or c == 9802: return "beverages"
    if 8000 <= c < 8010 or c == 8012: return "oils"
    if 8010 <= c < 8500: return "condiments"
    if 8800 <= c < 8900: return "sweets"
    return None  # baby foods, formula, human milk, uncategorised


def prep_state(desc, survey, cat):
    d = desc.lower()
    if re.search(r"\braw\b|\buncooked\b|\bdry\b|\bdried\b", d): return "RAW"
    if COOKED_KW.search(d): return "COOKED"
    if survey and cat in ("international", "indian", "fast_food", "rice"): return "PREPARED"
    return "NA"


def diet_class(desc, cat, survey):
    d = desc.lower()
    if MEAT_KW.search(d) or cat == "meat": return "NON_VEGETARIAN"
    if FISH_KW.search(d) or cat == "fish": return "PESCATARIAN"
    if survey:
        return "UNKNOWN"  # mixed survey foods: composition not reliably known
    if cat == "eggs" or EGG_KW.search(d): return "EGG"
    if cat in ("fruits", "vegetables", "legumes", "nuts", "grains", "rice") and not re.search(r"\b(with|prepared|batter|cheese|butter|cream|milk|egg|sauce|mix)\b", d):
        return "VEGAN"
    if cat in ("dairy", "milk") and not re.search(r"\bsoy|almond|rice milk|oat\b", d) and not re.search(r"\bwith\b", d):
        return "VEGETARIAN"
    return "UNKNOWN"


# ---- curated display names, aliases and popular flags --------------------------------------------
# (dataset, exact USDA description, display name or None, aliases, category override or None)
CURATED = [
    ("FNDDS", "Bread, chappatti or roti", "Roti / Chapati (whole wheat flatbread)", "roti chapati phulka fulka", "indian"),
    ("FNDDS", "Bread, paratha", "Paratha (plain)", "parantha parotta", "indian"),
    ("FNDDS", "Dal", "Dal (cooked lentils)", "daal dhal lentil", "indian"),
    ("FNDDS", "Lentil curry", None, "dal tadka dal fry", "indian"),
    ("FNDDS", "Sambar, vegetable stew", "Sambar", "sambhar", "indian"),
    ("FNDDS", "Idli", "Idli", "idly", "indian"),
    ("FNDDS", "Dosa, plain", "Dosa (plain)", "dosai", "indian"),
    ("FNDDS", "Dosa, with filling", "Masala dosa (with filling)", "masala dosa", "indian"),
    ("FNDDS", "Upma", "Upma", "rava upma", "indian"),
    ("FNDDS", "Biryani with chicken", "Chicken biryani", "biriyani", "indian"),
    ("FNDDS", "Biryani with vegetables", "Vegetable biryani", "veg biryani biriyani", "indian"),
    ("FNDDS", "Biryani with meat", "Mutton / meat biryani", "biriyani", "indian"),
    ("FNDDS", "Chicken curry", "Chicken curry", "murgh", "indian"),
    ("FNDDS", "Vegetable curry", "Vegetable curry (sabzi)", "sabji sabzi", "indian"),
    ("FNDDS", "Fish curry", "Fish curry", "machli", "indian"),
    ("FNDDS", "Palak Paneer", "Palak paneer", "saag paneer", "indian"),
    ("FNDDS", "Channa Saag", "Chana saag", "chole saag", "indian"),
    ("FNDDS", "Cheese, paneer", "Paneer", "cottage cheese indian", "indian"),
    ("FNDDS", "Samosa", "Samosa", "", "indian"),
    ("FNDDS", "Pakora", "Pakora", "pakoda bhajia", "indian"),
    ("FNDDS", "Buttermilk", "Buttermilk (chaas)", "chaas chhachh mattha", "milk"),
    ("FNDDS", "Chutney", None, "", "indian"),
    ("SR", "Rice, white, long-grain, regular, enriched, cooked", "Rice, white, cooked", "plain rice chawal steamed rice", None),
    ("SR", "Rice, brown, long-grain, cooked (Includes foods for USDA's Food Distribution Program)", "Rice, brown, cooked", "brown rice", None),
    ("SR", "Rice, white, long-grain, regular, raw, enriched", "Rice, white, raw (uncooked)", "chawal", None),
    ("SR", "Beans, kidney, red, mature seeds, cooked, boiled, without salt", "Rajma (red kidney beans), boiled", "rajma kidney beans", None),
    ("SR", "Chickpeas (garbanzo beans, bengal gram), mature seeds, cooked, boiled, without salt", "Chickpeas / chole (kabuli chana), boiled", "chole chana kabuli", None),
    ("SR", "Lentils, mature seeds, cooked, boiled, without salt", "Lentils (masoor), boiled", "masoor dal", None),
    ("SR", "Mung beans, mature seeds, sprouted, raw", "Sprouts (mung bean), raw", "sprouts moong", None),
    ("SR", "Mung beans, mature seeds, cooked, boiled, without salt", "Moong (mung beans), boiled", "moong mung green gram", None),
    ("SR", "Yogurt, plain, whole milk", "Curd / yogurt, plain, whole milk", "dahi curd", None),
    ("SR", "Milk, buttermilk, fluid, cultured, lowfat", "Buttermilk, cultured, low fat", "chaas", None),
    ("SR", "Butter oil, anhydrous", "Ghee (clarified butter)", "ghee", None),
    ("SR", "Milk, whole, 3.25% milkfat, with added vitamin D", "Milk, whole (3.25%)", "doodh full cream", None),
    ("SR", "Milk, reduced fat, fluid, 2% milkfat, with added vitamin A and vitamin D", "Milk, toned (2%)", "toned milk", None),
    ("SR", "Milk, nonfat, fluid, with added vitamin A and vitamin D (fat free or skim)", "Milk, skimmed", "skim milk", None),
    ("SR", "Egg, whole, raw, fresh", "Egg, whole, raw", "anda", None),
    ("SR", "Egg, whole, cooked, hard-boiled", "Egg, hard-boiled", "boiled egg anda", None),
    ("SR", "Egg, whole, cooked, scrambled", "Egg, scrambled", "bhurji", None),
    ("SR", "Bananas, raw", "Banana, raw", "kela", None),
    ("SR", "Apples, raw, with skin (Includes foods for USDA's Food Distribution Program)", "Apple, raw, with skin", "seb", None),
    ("SR", "Mangos, raw", "Mango, raw", "aam", None),
    ("SR", "Oranges, raw, all commercial varieties", "Orange, raw", "santra", None),
    ("SR", "Papayas, raw", "Papaya, raw", "papita", None),
    ("SR", "Grapes, red or green (European type, such as Thompson seedless), raw", "Grapes, raw", "angoor", None),
    ("SR", "Guavas, common, raw", "Guava, raw", "amrood", None),
    ("SR", "Pomegranates, raw", "Pomegranate, raw", "anar", None),
    ("SR", "Watermelon, raw", "Watermelon, raw", "tarbooz", None),
    ("SR", "Onions, raw", "Onion, raw", "pyaz", None),
    ("SR", "Tomatoes, red, ripe, raw, year round average", "Tomato, raw", "tamatar", None),
    ("SR", "Potatoes, boiled, cooked in skin, flesh, without salt", "Potato, boiled", "aloo", None),
    ("SR", "Spinach, raw", "Spinach, raw", "palak", None),
    ("SR", "Cauliflower, raw", "Cauliflower, raw", "gobi", None),
    ("SR", "Okra, raw", "Okra / bhindi, raw", "bhindi ladyfinger", None),
    ("SR", "Carrots, raw", "Carrot, raw", "gajar", None),
    ("SR", "Cucumber, with peel, raw", "Cucumber, raw", "kheera", None),
    ("SR", "Peas, green, raw", "Green peas, raw", "matar", None),
    ("SR", "Garlic, raw", "Garlic, raw", "lahsun", None),
    ("SR", "Ginger root, raw", "Ginger, raw", "adrak", None),
    ("SR", "Chickpea flour (besan)", "Besan (chickpea flour)", "besan gram flour", None),
    ("SR", "Wheat flour, whole-grain (Includes foods for USDA's Food Distribution Program)", "Atta (whole wheat flour)", "atta", None),
    ("SR", "Semolina, enriched", "Semolina / rava / sooji", "rava suji sooji", None),
    ("SR", "Oil, soybean, salad or cooking", "Soybean oil", "refined oil", None),
    ("SR", "Oil, sunflower, linoleic, (approx. 65%)", "Sunflower oil", "", None),
    ("SR", "Oil, mustard", "Mustard oil", "sarson", None),
    ("SR", "Oil, olive, salad or cooking", "Olive oil", "", None),
    ("SR", "Oil, coconut", "Coconut oil", "", None),
    ("SR", "Butter, salted", "Butter, salted", "makhan", None),
    ("SR", "Peanuts, all types, dry-roasted, without salt", "Peanuts, dry-roasted", "moongphali", None),
    ("SR", "Nuts, almonds", "Almonds", "badam", None),
    ("SR", "Nuts, cashew nuts, raw", "Cashews, raw", "kaju", None),
    ("SR", "Oats (Includes foods for USDA's Food Distribution Program)", "Oats, rolled (dry)", "oatmeal", None),
    ("SR", "Bread, whole-wheat, commercially prepared", "Bread, whole wheat", "brown bread", None),
    ("SR", "Bread, white, commercially prepared (includes soft bread crumbs)", "Bread, white", "", None),
    ("SR", "Sugars, granulated", "Sugar, white", "cheeni", None),
    ("SR", "Honey", "Honey", "shahad", None),
    ("SR", "Chicken, broilers or fryers, breast, meat only, cooked, roasted", "Chicken breast, roasted (meat only)", "", None),
    ("SR", "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw", "Chicken breast, raw (meat only)", "", None),
    ("SR", "Fish, salmon, Atlantic, farmed, cooked, dry heat", "Salmon, Atlantic, cooked", "", None),
    ("SR", "Fish, tuna, light, canned in water, drained solids (Includes foods for USDA's Food Distribution Program)", "Tuna, canned in water", "", None),
    ("SR", "Tofu, raw, firm, prepared with calcium sulfate", "Tofu, firm", "", None),
    ("SR", "Beverages, tea, black, brewed, prepared with tap water", "Tea, black, brewed (no milk)", "chai", None),
    ("SR", "Beverages, coffee, brewed, prepared with tap water", "Coffee, brewed (black)", "", None),
    ("SR", "Cheese, cheddar (Includes foods for USDA's Food Distribution Program)", "Cheddar cheese", "", None),
    ("SR", "Seeds, flaxseed", "Flaxseeds", "alsi", None),
    ("SR", "Peanut butter, smooth style, without salt", "Peanut butter, smooth", "", None),
]

# Home-style estimates: calculated from standard recipes of SR Legacy ingredients.
# Each: id, name, aliases, ingredients [(SR description, grams)], cooked yield factor (final weight /
# sum of ingredient weights; 1.0 when ingredients are already cooked), piece serving (label, grams) or None.
ESTIMATES = [
    ("poha", "Poha (flattened rice, home-style)", "poha pohe aval",
     [("Rice, white, long-grain, regular, enriched, cooked", 150), ("Onions, raw", 25), ("Peanuts, all types, dry-roasted, without salt", 8),
      ("Oil, soybean, salad or cooking", 7), ("Peas, green, raw", 10)], 1.0, ("PLATE", "1 plate (as in recipe)", 200)),
    ("khichdi", "Khichdi (rice & moong dal, home-style)", "khichri kitchari",
     [("Rice, white, long-grain, regular, enriched, cooked", 150), ("Mung beans, mature seeds, cooked, boiled, without salt", 100), ("Butter oil, anhydrous", 5)], 1.0, None),
    ("rajma_curry", "Rajma curry (home-style)", "rajma masala",
     [("Beans, kidney, red, mature seeds, cooked, boiled, without salt", 150), ("Onions, raw", 30), ("Tomatoes, red, ripe, raw, year round average", 50),
      ("Oil, soybean, salad or cooking", 8), ("Beverages, water, tap, drinking", 60)], 0.9, None),
    ("chole_masala", "Chole masala (home-style)", "chole chana masala chickpea curry",
     [("Chickpeas (garbanzo beans, bengal gram), mature seeds, cooked, boiled, without salt", 150), ("Onions, raw", 30), ("Tomatoes, red, ripe, raw, year round average", 50),
      ("Oil, soybean, salad or cooking", 8), ("Beverages, water, tap, drinking", 60)], 0.9, None),
    ("besan_chilla", "Besan chilla (gram-flour pancake, home-style)", "cheela chila pudla",
     [("Chickpea flour (besan)", 50), ("Beverages, water, tap, drinking", 60), ("Onions, raw", 15), ("Tomatoes, red, ripe, raw, year round average", 15), ("Oil, soybean, salad or cooking", 5)], 0.85, ("PIECE", "1 chilla (from 50 g besan)", 123)),
    ("dhokla", "Dhokla (steamed gram-flour cake, home-style)", "khaman dhokla",
     [("Chickpea flour (besan)", 100), ("Beverages, water, tap, drinking", 110), ("Yogurt, plain, whole milk", 30), ("Sugars, granulated", 5), ("Oil, soybean, salad or cooking", 5)], 0.95, ("PIECE", "1 piece (≈ 1/8 of recipe)", 31)),
    ("thepla", "Thepla (methi flatbread, home-style)", "methi thepla",
     [("Wheat flour, whole-grain (Includes foods for USDA's Food Distribution Program)", 100), ("Chickpea flour (besan)", 10), ("Spinach, raw", 20),
      ("Yogurt, plain, whole milk", 20), ("Oil, soybean, salad or cooking", 10), ("Beverages, water, tap, drinking", 40)], 0.85, ("PIECE", "1 thepla (≈ 1/6 of recipe)", 28)),
    ("paneer_bhurji", "Paneer bhurji (home-style)", "paneer bhurji",
     [("Cheese, cottage, creamed, large or small curd", 0)], 1.0, None),  # placeholder, removed below
]
ESTIMATES = [e for e in ESTIMATES if e[0] != "paneer_bhurji"]


def main():
    root, out = sys.argv[1], sys.argv[2]
    _, sr_foods, sr_vals, sr_por = load_dataset(root, "FoodData_Central_sr_legacy_food_csv_2018-04")
    sv_dir, sv_foods, sv_vals, sv_por = load_dataset(root, "FoodData_Central_survey_food_csv_2024-10-31")
    wweia = {r["fdc_id"]: r["wweia_category_number"] for r in read_csv(os.path.join(sv_dir, "survey_fndds_food.csv"))}
    sr_by_desc = {f["description"]: k for k, f in sr_foods.items()}
    sv_by_desc = {f["description"]: k for k, f in sv_foods.items()}

    curated = {}
    missing = []
    for ds, desc, name, aliases, cat in CURATED:
        idx = sr_by_desc if ds == "SR" else sv_by_desc
        fid = idx.get(desc)
        if fid is None:
            missing.append((ds, desc)); continue
        curated[(ds, fid)] = (name, aliases, cat)
    if missing:
        print("WARNING curated items not found:", *missing, sep="\n  ", file=sys.stderr)

    foods = []
    def add(ds, fid, f, vals, por, cat, survey):
        c = curated.get((ds, fid))
        desc = f["description"]
        name = (c[0] if c and c[0] else desc)
        if c and c[2]: cat = c[2]
        elif INDIAN_KW.search(desc) and survey: cat = "indian"
        nv = [None if v is None else round(v, 4) for v in vals]
        if nv[0] is None:
            return  # no energy value — skip rather than present incomplete core data
        sv = portions_for(por, survey)
        density = None
        if cat in ("beverages", "milk"):
            cups = [p for p in sv if p[0] == "CUP"]
            if cups: density = round(cups[0][2] / 236.5882365, 4)
        rec = {
            "id": f"usda-{'sr' if ds == 'SR' else 'fndds'}-{fid}", "n": name, "c": cat,
            "src": "USDA_SR_LEGACY" if ds == "SR" else "USDA_FNDDS", "ref": fid,
            "q": "VERIFIED_DATABASE" if ds == "SR" else "GENERIC_ESTIMATE",
            "p": prep_state(desc, survey, cat), "d": diet_class(desc, cat, survey),
            "nv": nv, "sv": sv, "pub": f.get("publication_date") or None,
        }
        if name != desc: rec["orig"] = desc
        if c and c[1]: rec["al"] = c[1]
        if c: rec["pop"] = 1
        if density: rec["den"] = density
        foods.append(rec)

    for fid, f in sr_foods.items():
        cat = sr_category(f["food_category_id"], f["description"])
        if cat is None: continue
        if cat == "fast_food" and not f["description"].startswith("Fast foods,"): continue
        add("SR", fid, f, sr_vals[fid], sr_por.get(fid, []), cat, False)
    for fid, f in sv_foods.items():
        cat = wweia_category(wweia.get(fid, "9999"))
        if cat is None: continue
        if re.search(r"\bas ingredient\b", f["description"], re.I): continue
        add("FNDDS", fid, f, sv_vals[fid], sv_por.get(fid, []), cat, True)

    # Home-style estimates
    for key, name, aliases, ings, yld, piece in ESTIMATES:
        total = [None] * len(NUTRIENTS)
        known = [0] * len(NUTRIENTS)
        weight = 0.0
        parts = []
        for desc, g in ings:
            fid = sr_by_desc.get(desc)
            if fid is None:
                raise SystemExit(f"Estimate {key}: ingredient not found: {desc}")
            weight += g
            parts.append(f"{g:g} g {desc} (FDC {fid})")
            for i, v in enumerate(sr_vals[fid]):
                if v is not None:
                    total[i] = (total[i] or 0.0) + v * g / 100.0
                    known[i] += 1
        final_w = weight * yld
        # Only keep nutrients known for every ingredient (otherwise the estimate would understate them).
        nv = [round(t / final_w * 100.0, 4) if t is not None and known[i] == len(ings) else None for i, t in enumerate(total)]
        sv = [[piece[0], piece[1], piece[2]]] if piece else []
        rec = {
            "id": f"est-{key}", "n": name, "c": "indian", "src": "RECIPE", "ref": None, "q": "CALCULATED", "p": "PREPARED",
            "d": "VEGETARIAN" if any("Yogurt" in d or "Butter oil" in d for d, _ in ings) else "VEGAN",
            "nv": nv, "sv": sv, "pub": None, "al": aliases, "pop": 1,
            "note": f"Home-style estimate calculated from a standard recipe: {'; '.join(parts)}. Cooked yield factor {yld:g} (final weight {final_w:.0f} g). Actual dishes vary with recipe and oil used.",
        }
        foods.append(rec)

    foods.sort(key=lambda r: (r["c"], r["n"].lower()))
    doc = {
        "version": SEED_VERSION,
        "nutrients": [k for k, _ in NUTRIENTS],
        "categories": [{"id": i, "name": n, "emoji": e, "order": o} for o, (i, n, e) in enumerate(CATEGORIES)],
        "sources": [
            {"id": "USDA_SR_LEGACY", "title": "USDA FoodData Central – SR Legacy (April 2018)", "license": "Public domain (CC0 1.0)", "url": "https://fdc.nal.usda.gov/"},
            {"id": "USDA_FNDDS", "title": "USDA FoodData Central – FNDDS 2021–2023 (released 2024-10-31)", "license": "Public domain (CC0 1.0)", "url": "https://fdc.nal.usda.gov/"},
        ],
        "foods": foods,
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    from collections import Counter
    print(f"{len(foods)} foods written to {out} ({os.path.getsize(out)/1e6:.2f} MB)")
    print(Counter(r["c"] for r in foods).most_common())
    print("popular:", sum(1 for r in foods if r.get("pop")))


if __name__ == "__main__":
    main()
