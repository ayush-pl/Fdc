package com.fdc.nutrition.ui.food

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.Fmt
import com.fdc.nutrition.ui.navigation.FoodEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ServingRow(unit: QuantityUnit = QuantityUnit.PIECE, label: String = "", amount: String = "", val id: String = "", val source: String? = "USER") {
    var unit by mutableStateOf(unit); var label by mutableStateOf(label); var amount by mutableStateOf(amount)
}

@HiltViewModel
class FoodEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val imports: ProductImportRepository,
    private val drafts: PendingDraftStore,
) : BaseViewModel() {
    val route = savedState.toRoute<FoodEditorRoute>()
    val categories = foods.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var name by mutableStateOf(""); var brand by mutableStateOf(""); var categoryId by mutableStateOf("homemade")
    var basis by mutableStateOf(NutrientBasis.GRAM); var preparation by mutableStateOf(PreparationState.NOT_APPLICABLE)
    var barcode by mutableStateOf(route.barcode ?: ""); var notes by mutableStateOf("")
    var perServing by mutableStateOf(false); var labelServing by mutableStateOf("")
    val values = mutableStateMapOf<Nutrient, String>()
    val servings = mutableStateListOf<ServingRow>()
    var dietClass by mutableStateOf(DietClass.UNKNOWN)
    var allergenKnown by mutableStateOf(false)
    val allergens = mutableStateListOf<String>()
    var errors by mutableStateOf<List<String>>(emptyList())
    var warnings by mutableStateOf<List<String>>(emptyList())
    var duplicates by mutableStateOf<List<Food>>(emptyList())
    var reviewNote by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    private var importedDraft: FoodDraft? = null
    val isEdit get() = route.foodId != null
    val isImport get() = importedDraft != null

    init {
        viewModelScope.launch {
            route.foodId?.let { id -> foods.getFood(id)?.let { load(it.toDraft()) } }
            route.draftKey?.let { k -> drafts.get(k)?.let { (d, note) -> importedDraft = d; reviewNote = note; load(d) } }
            if (route.barcode != null && route.draftKey == null && route.foodId == null) { categoryId = "packaged"; reviewNote = "This barcode wasn't found. Enter the details from the package label." }
        }
    }

    private fun Food.toDraft() = FoodDraft(id, name, brand, categoryId, basis, nutrientsPer100, servings, preparation, barcodes.firstOrNull(), dietClass, allergens, allergenInfoKnown, densityGPerMl, source, sourceRef, dataQuality, notes)

    private fun load(d: FoodDraft) {
        name = d.name; brand = d.brand ?: ""; categoryId = d.categoryId; basis = d.basis; preparation = d.preparation
        barcode = d.barcode ?: barcode; notes = d.notes ?: ""; dietClass = d.dietClass; allergenKnown = d.allergenInfoKnown
        allergens.clear(); allergens.addAll(d.allergens)
        values.clear(); d.nutrientsPer100.values.forEach { (n, v) -> values[n] = Fmt.num(v, 3) }
        servings.clear(); d.servings.forEach { servings += ServingRow(it.unit, it.label, Fmt.num(it.amount, 2), it.id, it.source) }
    }

    private fun buildDraft(): FoodDraft? {
        val factor = if (perServing) {
            val s = Fmt.parse(labelServing)?.takeIf { it > 0 } ?: run { errors = listOf("Enter the label's serving size to convert values to per 100 ${basis.symbol}."); return null }
            100.0 / s
        } else 1.0
        val map = values.mapNotNull { (n, s) -> if (s.isBlank()) null else Fmt.parse(s)?.let { n to it * factor } }.toMap()
        val bad = values.filter { it.value.isNotBlank() && Fmt.parse(it.value) == null }.keys
        if (bad.isNotEmpty()) { errors = bad.map { "${it.displayName}: not a number" }; return null }
        val rows = servings.filter { it.amount.isNotBlank() || it.label.isNotBlank() }.map {
            ServingSize(it.id, it.unit, it.label.ifBlank { "1 ${it.unit.symbol}" }, Fmt.parse(it.amount) ?: -1.0, source = it.source ?: "USER")
        }.toMutableList()
        if (perServing && rows.none { it.unit == QuantityUnit.SERVING }) Fmt.parse(labelServing)?.let { rows.add(0, ServingSize("", QuantityUnit.SERVING, "1 serving (label)", it, isDefault = true, source = "USER")) }
        val base = importedDraft
        return FoodDraft(
            id = route.foodId, name = name, brand = brand.ifBlank { null }, categoryId = categoryId, basis = basis, nutrientsPer100 = Nutrients(map),
            servings = rows, preparation = preparation, barcode = barcode.ifBlank { null }, dietClass = dietClass, allergens = allergens.toSet(),
            allergenInfoKnown = allergenKnown, densityGPerMl = base?.densityGPerMl, source = base?.source ?: FoodSource.USER, sourceRef = base?.sourceRef,
            dataQuality = if (base != null && !edited(base, map)) base.dataQuality else if (base != null) DataQuality.USER_ENTERED else DataQuality.USER_ENTERED,
            notes = notes.ifBlank { null },
        )
    }

    /** Imported label data the user corrected is re-labelled as user-entered. */
    private fun edited(base: FoodDraft, map: Map<Nutrient, Double>) = base.nutrientsPer100.values.keys != map.keys || map.any { (k, v) -> kotlin.math.abs((base.nutrientsPer100[k] ?: -1.0) - v) > 1e-3 }

    fun validate(): FoodDraft? {
        val d = buildDraft() ?: return null
        errors = foods.validate(d)
        warnings = foods.warnings(d)
        return if (errors.isEmpty()) d else null
    }

    fun save(force: Boolean = false, onSaved: (String) -> Unit) {
        if (saving) return
        val d = validate() ?: return
        saving = true
        viewModelScope.launch {
            try {
                if (!force && !isEdit) {
                    val dups = foods.possibleDuplicates(d)
                    if (dups.isNotEmpty()) { duplicates = dups; return@launch }
                }
                val id = if (isImport) imports.saveImported(d) else foods.saveFood(d)
                route.draftKey?.let { drafts.remove(it) }
                message(if (isEdit) "Food updated" else "Food saved")
                onSaved(id)
            } catch (e: Exception) { errors = (e.message ?: "Couldn't save").split("\n") } finally { saving = false }
        }
    }
}
