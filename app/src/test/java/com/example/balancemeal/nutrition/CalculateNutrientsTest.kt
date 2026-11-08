package com.example.balancemeal.nutrition

import com.example.balancemeal.model.Nutrients
import org.junit.Assert.*
import org.junit.Test

class CalculateNutrientsTest {
    @Test fun allValuesScaleWithoutRoundingAndEnergyUsesSourceValue() {
        val actual = calculateNutrients(Nutrients(37.33, 3.36, 0.32, 166.0), 125.0)!!
        assertEquals(46.6625, actual.carbohydrateG, 0.000000001)
        assertEquals(4.2, actual.proteinG, 0.000000001)
        assertEquals(0.4, actual.fatG, 0.000000001)
        assertEquals(207.5, actual.energyKcal, 0.0)
    }

    @Test fun zeroNegativeAndNonFiniteAmountsNeverProduceNutrition() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNull(calculateNutrients(Nutrients(1.0, 2.0, 3.0, 99.0), it))
        }
    }

    @Test fun overflowIsInvalidButZeroNutrientFoodsAreValid() {
        assertNull(calculateNutrients(Nutrients(Double.MAX_VALUE, 0.0, 0.0, 0.0), 200.0))
        assertEquals(Nutrients(0.0, 0.0, 0.0, 0.0),
            calculateNutrients(Nutrients(0.0, 0.0, 0.0, 0.0), 100.0))
    }
}
