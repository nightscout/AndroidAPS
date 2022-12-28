package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Food
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.food.NSFood

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
