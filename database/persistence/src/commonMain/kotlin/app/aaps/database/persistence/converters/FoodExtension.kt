package app.aaps.database.persistence.converters

import app.aaps.core.data.model.FD
import app.aaps.database.entities.Food

fun Food.fromDb(): FD =
    FD(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        portion = this.portion,
        carbs = this.carbs,
        fat = this.fat,
        protein = this.protein,
        energy = this.energy,
        unit = this.unit,
        gi = this.gi,
        ids = this.interfaceIDs.fromDb()
    )

fun FD.toDb(): Food =
    Food(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        portion = this.portion,
        carbs = this.carbs,
        fat = this.fat,
        protein = this.protein,
        energy = this.energy,
        unit = this.unit,
        gi = this.gi,
        interfaceIDs_backing = this.ids.toDb()
    )
