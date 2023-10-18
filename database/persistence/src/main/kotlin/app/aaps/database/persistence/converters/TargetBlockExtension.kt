package app.aaps.database.persistence.converters

import app.aaps.core.data.db.data.TargetBlock

fun app.aaps.database.entities.data.TargetBlock.fromDb(): TargetBlock = TargetBlock(duration = this.duration, lowTarget = this.lowTarget, highTarget = this.highTarget)
fun TargetBlock.toDb(): app.aaps.database.entities.data.TargetBlock = app.aaps.database.entities.data.TargetBlock(duration = this.duration, lowTarget = this.lowTarget, highTarget = this.highTarget)
