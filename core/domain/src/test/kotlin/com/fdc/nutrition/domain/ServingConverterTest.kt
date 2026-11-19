package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServingConverterTest {
    @Test fun `mass units convert universally for gram-based foods`() {
        val kg = ServingConverter.toBaseAmount(0.15, QuantityUnit.KILOGRAM, NutrientBasis.GRAM, emptyList()).getOrThrow()
        assertThat(kg).isWithin(1e-9).of(150.0)
        val oz = ServingConverter.toBaseAmount(1.0, QuantityUnit.OUNCE, NutrientBasis.GRAM, emptyList()).getOrThrow()
        assertThat(oz).isWithin(1e-9).of(28.349523125)
    }

    @Test fun `cup uses food-specific weight, never a universal weight`() {
        val g = ServingConverter.toBaseAmount(1.0, QuantityUnit.CUP, NutrientBasis.GRAM, Fx.rice.servings).getOrThrow()
        assertThat(g).isEqualTo(158.0)
    }

    @Test fun `household units without food-specific data are unsupported`() {
        listOf(QuantityUnit.PIECE, QuantityUnit.BOWL, QuantityUnit.PLATE, QuantityUnit.SLICE, QuantityUnit.SERVING, QuantityUnit.TABLESPOON).forEach { u ->
            val r = ServingConverter.toBaseAmount(1.0, u, NutrientBasis.GRAM, emptyList())
            assertThat(r.isFailure).isTrue()
            assertThat(r.exceptionOrNull()).isInstanceOf(ConversionError.UnsupportedUnit::class.java)
        }
    }

    @Test fun `volume units convert for millilitre-based foods`() {
        assertThat(ServingConverter.toBaseAmount(1.5, QuantityUnit.LITRE, NutrientBasis.MILLILITRE, emptyList()).getOrThrow()).isWithin(1e-9).of(1500.0)
        assertThat(ServingConverter.toBaseAmount(2.0, QuantityUnit.TABLESPOON, NutrientBasis.MILLILITRE, emptyList()).getOrThrow()).isWithin(1e-9).of(29.5735295625)
    }

    @Test fun `density bridges mass and volume when known`() {
        val opts = ServingConverter.optionsFor(Fx.milk)
        val grams = opts.first { it.unit == QuantityUnit.GRAM }
        assertThat(grams.baseAmount(103.0)).isWithin(1e-9).of(100.0)
        // Without density, grams are not offered for a ml-based food
        assertThat(ServingConverter.optionsFor(Fx.milk.copy(densityGPerMl = null)).none { it.unit == QuantityUnit.GRAM }).isTrue()
    }

    @Test fun `piece serving works when defined for the food`() {
        val roti = Fx.rice.copy(servings = listOf(ServingSize("p", QuantityUnit.PIECE, "1 medium roti", 40.0, isDefault = true)))
        assertThat(ServingConverter.toBaseAmount(3.0, QuantityUnit.PIECE, roti.basis, roti.servings).getOrThrow()).isEqualTo(120.0)
        assertThat(ServingConverter.defaultOption(roti).unit).isEqualTo(QuantityUnit.PIECE)
    }

    @Test fun `invalid quantities are rejected`() {
        assertThat(ServingConverter.toBaseAmount(0.0, QuantityUnit.GRAM, NutrientBasis.GRAM, emptyList()).isFailure).isTrue()
        assertThat(ServingConverter.toBaseAmount(-5.0, QuantityUnit.GRAM, NutrientBasis.GRAM, emptyList()).isFailure).isTrue()
    }

    @Test fun `body unit conversions round trip`() {
        assertThat(BodyUnits.lbToKg(BodyUnits.kgToLb(72.5))).isWithin(1e-9).of(72.5)
        assertThat(BodyUnits.inToCm(1.0)).isEqualTo(2.54)
    }
}
