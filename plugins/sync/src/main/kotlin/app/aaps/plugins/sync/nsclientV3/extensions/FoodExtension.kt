package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.nssdk.localmodel.food.NSFood

fun NSFood.toFood(): FD =
    FD(
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
        ids = IDs(nightscoutId = identifier)
    )

fun FD.toNSFood(): NSFood =
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
        identifier = ids.nightscoutId,
    )
