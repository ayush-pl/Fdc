package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fdc.nutrition.BuildConfig
import com.fdc.nutrition.domain.model.DataQuality
import com.fdc.nutrition.ui.components.AppCard
import com.fdc.nutrition.ui.components.QualityBadge
import com.fdc.nutrition.ui.navigation.BackTopBar

@Composable
fun AboutScreen(nav: NavHostController) {
    Scaffold(topBar = { BackTopBar("About", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FDC Nutrition ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("Plan your nutrition → record your food → monitor your intake → analyse your progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppCard {
                Text("Food data sources", style = MaterialTheme.typography.titleSmall)
                Text("• USDA FoodData Central — SR Legacy (2018) and FNDDS 2021–2023 survey foods. U.S. Department of Agriculture, Agricultural Research Service. Public domain (CC0 1.0). Bundled for offline use with FDC IDs preserved.")
                Text("• Open Food Facts — packaged products by barcode. Open Database License (ODbL 1.0); community-contributed.")
                Text("• USDA FoodData Central API — optional online search (public domain).")
                Text("• A few Indian dishes absent from USDA (poha, khichdi, dhokla, thepla, besan chilla, rajma and chole curries) are home-style estimates calculated from USDA ingredients; each food shows its recipe and is labelled as calculated.")
            }
            AppCard {
                Text("Data quality labels", style = MaterialTheme.typography.titleSmall)
                DataQuality.entries.forEach { q -> Row(Modifier.padding(vertical = 4.dp)) { QualityBadge(q); Spacer(Modifier.width(8.dp)); Text(q.description, style = MaterialTheme.typography.bodySmall) } }
            }
            AppCard {
                Text("Calculations", style = MaterialTheme.typography.titleSmall)
                Text("Nutrients = (amount ÷ 100) × value per 100 g or ml. Household measures use food-specific weights. Energy targets use Mifflin–St Jeor × activity factor; weight change assumes ≈7,700 kcal per kg. Missing nutrient data is shown as unknown — never counted as zero.")
                Text("Calories burned are not shown because no verified activity integration is connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("This app provides general nutrition information and is not medical advice. Consult a qualified professional for medical or dietary guidance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
