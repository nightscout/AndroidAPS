package app.aaps.workflow.iob

import app.aaps.core.data.model.CA
import app.aaps.core.keys.DoubleKey
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class CarbsInPastExtensionTest : TestBaseWithProfile() {

    @Test
    fun `fromCarbs with isAAPSOrWeighted true calculates 5-minute carb impact correctly`() {
        // Arrange
        val carbAmount = 30.0
        val t = CA(timestamp = now, amount = carbAmount, duration = 0)
        val isOref1 = false
        val isAAPSOrWeighted = true
        val sens = 100.0
        val ic = 10.0
        val maxAbsorptionHours = 3.0

        whenever(preferences.get(DoubleKey.AbsorptionMaxTime)).thenReturn(maxAbsorptionHours)

        // Expected calculation: carbs / (maxAbsorptionHours * 60 / 5) * sens / ic
        // 30.0 / (3.0 * 12) * 100.0 / 10.0 = 30.0 / 36.0 * 10.0 = 0.8333... * 10.0 = 8.333...
        val expectedMin5minCarbImpact = 8.333333333333334

        // Act
        val result = fromCarbs(t, isOref1, isAAPSOrWeighted, sens, ic, aapsLogger, dateUtil, preferences)

        // Assert
        Assertions.assertEquals(now, result.time)
        Assertions.assertEquals(carbAmount, result.carbs, 0.001)
        Assertions.assertEquals(carbAmount, result.remaining, 0.001)
        Assertions.assertEquals(expectedMin5minCarbImpact, result.min5minCarbImpact, 0.001)
    }

    @Test
    fun `fromCarbs with isOref1 true gets SMB 5-minute carb impact from preferences`() {
        // Arrange
        val carbAmount = 20.0
        val t = CA(timestamp = now, amount = carbAmount, duration = 0)
        val isOref1 = true
        val isAAPSOrWeighted = false
        val sens = 120.0
        val ic = 15.0
        val expectedMin5minCarbImpact = 8.0 // Value from preferences

        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(expectedMin5minCarbImpact)

        // Act
        val result = fromCarbs(t, isOref1, isAAPSOrWeighted, sens, ic, aapsLogger, dateUtil, preferences)

        // Assert
        Assertions.assertEquals(now, result.time)
        Assertions.assertEquals(carbAmount, result.carbs, 0.001)
        Assertions.assertEquals(carbAmount, result.remaining, 0.001)
        Assertions.assertEquals(expectedMin5minCarbImpact, result.min5minCarbImpact, 0.001)
    }

    @Test
    fun `fromCarbs with isOref1 false gets AMA 5-minute carb impact from preferences`() {
        // Arrange
        val carbAmount = 15.0
        val t = CA(timestamp = now, amount = carbAmount, duration = 0)
        val isOref1 = false
        val isAAPSOrWeighted = false
        val sens = 90.0
        val ic = 12.0
        val expectedMin5minCarbImpact = 7.0 // Value from preferences

        whenever(preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)).thenReturn(expectedMin5minCarbImpact)

        // Act
        val result = fromCarbs(t, isOref1, isAAPSOrWeighted, sens, ic, aapsLogger, dateUtil, preferences)

        // Assert
        Assertions.assertEquals(now, result.time)
        Assertions.assertEquals(carbAmount, result.carbs, 0.001)
        Assertions.assertEquals(carbAmount, result.remaining, 0.001)
        Assertions.assertEquals(expectedMin5minCarbImpact, result.min5minCarbImpact, 0.001)
    }
}