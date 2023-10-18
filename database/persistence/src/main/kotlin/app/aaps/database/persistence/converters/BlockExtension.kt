package app.aaps.database.persistence.converters

import app.aaps.core.data.db.data.Block

fun app.aaps.database.entities.data.Block.fromDb(): Block = Block(duration = this.duration, amount = this.amount)
fun Block.toDb(): app.aaps.database.entities.data.Block = app.aaps.database.entities.data.Block(duration = this.duration, amount = this.amount)
