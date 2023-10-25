package app.aaps.core.interfaces.utils

import java.text.DecimalFormat

/**
 * Format double values to strings
 */
interface DecimalFormatter {

    fun to0Decimal(value: Double): String
    fun to0Decimal(value: Double, unit: String): String
    fun to1Decimal(value: Double): String
    fun to1Decimal(value: Double, unit: String): String
    fun to2Decimal(value: Double): String
    fun to2Decimal(value: Double, unit: String): String
    fun to3Decimal(value: Double): String
    fun to3Decimal(value: Double, unit: String): String
    fun toPumpSupportedBolus(value: Double, bolusStep: Double): String
    fun toPumpSupportedBolusWithUnits(value: Double, bolusStep: Double): String
    fun pumpSupportedBolusFormat(bolusStep: Double): DecimalFormat
}