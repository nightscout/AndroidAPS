package app.aaps.database.persistence.converters

import app.aaps.core.data.model.PS
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.database.entities.ProfileSwitch

fun ProfileSwitch.fromDb(): PS =
    PS(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        ids = this.interfaceIDs.fromDb(),
        basalBlocks = mutableListOf<Block>().also { list -> this.basalBlocks.forEach { list.add(it.fromDb()) } },
        isfBlocks = mutableListOf<Block>().also { list -> this.isfBlocks.forEach { list.add(it.fromDb()) } },
        icBlocks = mutableListOf<Block>().also { list -> this.icBlocks.forEach { list.add(it.fromDb()) } },
        targetBlocks = mutableListOf<TargetBlock>().also { list -> this.targetBlocks.forEach { list.add(it.fromDb()) } },
        glucoseUnit = this.glucoseUnit.fromDb(),
        profileName = this.profileName,
        timeshift = this.timeshift,
        percentage = this.percentage,
        duration = this.duration,
        iCfg = this.insulinConfiguration.fromDb()
    )

fun PS.toDb(): ProfileSwitch =
    ProfileSwitch(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        interfaceIDs_backing = this.ids.toDb(),
        basalBlocks = mutableListOf<app.aaps.database.entities.data.Block>().also { list -> this.basalBlocks.forEach { list.add(it.toDb()) } },
        isfBlocks = mutableListOf<app.aaps.database.entities.data.Block>().also { list -> this.isfBlocks.forEach { list.add(it.toDb()) } },
        icBlocks = mutableListOf<app.aaps.database.entities.data.Block>().also { list -> this.icBlocks.forEach { list.add(it.toDb()) } },
        targetBlocks = mutableListOf<app.aaps.database.entities.data.TargetBlock>().also { list -> this.targetBlocks.forEach { list.add(it.toDb()) } },
        glucoseUnit = this.glucoseUnit.toDb(),
        profileName = this.profileName,
        timeshift = this.timeshift,
        percentage = this.percentage,
        duration = this.duration,
        insulinConfiguration = this.iCfg.toDb()
    )
