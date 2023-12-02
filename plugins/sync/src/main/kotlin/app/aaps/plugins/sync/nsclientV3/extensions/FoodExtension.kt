package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs

fun NSFood.toFood(): Food =
    Food(
        isValid = isValid,
        name = name,
        category = category,
        subCategory = subCategory,
        portion = portion,
        carbs = carbs,
        fat = fat,
        protein = protein,
        energy = energy,
        unit = unit,
        gi = gi,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier)
    )

fun Food.toNSFood(): NSFood =
    NSFood(
        date = System.currentTimeMillis(),
        isValid = isValid,
        name = name,
        category = category,
        subCategory = subCategory,
        portion = portion,
        carbs = carbs,
        fat = fat,
        protein = protein,
        energy = energy,
        unit = unit,
        gi = gi,
        identifier = interfaceIDs.nightscoutId,
    )
