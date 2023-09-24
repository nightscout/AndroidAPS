package app.aaps.core.nssdk.mapper

import com.google.gson.Gson
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.remotemodel.RemoteFood

/**
 * Convert to [RemoteFood] and back to [NSFood]
 * testing purpose only
 *
 * @return treatment after double conversion
 */
fun NSFood.convertToRemoteAndBack(): NSFood? =
    toRemoteFood().toNSFood()

fun String.toNSFood(): NSFood? =
    Gson().fromJson(this, RemoteFood::class.java).toNSFood()

internal fun RemoteFood.toNSFood(): NSFood? {
    when (type) {
        "food" ->
            return NSFood(
                date = date ?: 0L,
                device = device,
                identifier = identifier,
                unit = unit ?: "g",
                srvModified = srvModified,
                srvCreated = srvCreated,
                subject = subject,
                isReadOnly = isReadOnly ?: false,
                isValid = isValid ?: true,
                name = name,
                category = category,
                subCategory = subcategory,
                portion = portion,
                carbs = carbs,
                fat = fat,
                protein = protein,
                energy = energy,
                gi = gi
            )

        else   -> return null
    }
}

internal fun NSFood.toRemoteFood(): RemoteFood =
    RemoteFood(
        type = "food",
        date = date,
        device = device,
        identifier = identifier,
        unit = unit,
        srvModified = srvModified,
        srvCreated = srvCreated,
        subject = subject,
        isReadOnly = isReadOnly,
        isValid = isValid,
        name = name,
        category = category,
        subcategory = subCategory,
        portion = portion,
        carbs = carbs,
        fat = fat,
        protein = protein,
        energy = energy,
        gi = gi
    )