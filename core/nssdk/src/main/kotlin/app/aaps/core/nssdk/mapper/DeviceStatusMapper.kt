package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.remotemodel.RemoteDeviceStatus

fun NSDeviceStatus.convertToRemoteAndBack(): NSDeviceStatus =
    toRemoteDeviceStatus().toNSDeviceStatus()

fun String.toNSDeviceStatus(): NSDeviceStatus =
    nsSdkJson.decodeFromString(RemoteDeviceStatus.serializer(), this).toNSDeviceStatus()

internal fun RemoteDeviceStatus.toNSDeviceStatus(): NSDeviceStatus =
    NSDeviceStatus(
        app = app,
        identifier = identifier,
        srvCreated = srvCreated,
        srvModified = srvModified,
        createdAt = createdAt,
        date = date,
        uploaderBattery = uploaderBattery,
        isCharging = isCharging,
        device = device,
        uploader = NSDeviceStatus.Uploader(uploader?.battery),
        pump = pump?.toNSDeviceStatusPump(),
        openaps = openaps?.toNSDeviceStatusOpenAps()
    )

internal fun NSDeviceStatus.toRemoteDeviceStatus(): RemoteDeviceStatus =
    RemoteDeviceStatus(
        app = app,
        identifier = identifier,
        srvCreated = srvCreated,
        srvModified = srvModified,
        createdAt = createdAt,
        date = date,
        uploaderBattery = uploaderBattery,
        isCharging = isCharging,
        device = device,
        uploader = RemoteDeviceStatus.Uploader(uploader?.battery),
        pump = pump?.toRemoteDeviceStatusPump(),
        openaps = openaps?.toRemoteDeviceStatusOpenAps()
    )

// The schema-less subtrees (pump.extended, openaps.suggested / enacted / iob) used to be Gson trees
// on the remote side and kotlinx trees on the local side, so every one of them was rebuilt by
// printing it to text and parsing it back. Both sides are kotlinx now, so they are carried straight
// across - no copy, no reparse, and no chance of the round trip changing anything.

internal fun RemoteDeviceStatus.Pump.toNSDeviceStatusPump(): NSDeviceStatus.Pump =
    NSDeviceStatus.Pump(
        clock = clock,
        reservoir = reservoir,
        reservoirDisplayOverride = reservoirDisplayOverride,
        battery = NSDeviceStatus.Pump.Battery(battery?.percent, battery?.voltage),
        status = NSDeviceStatus.Pump.Status(status?.status, status?.timestamp),
        extended = extended
    )

internal fun NSDeviceStatus.Pump.toRemoteDeviceStatusPump(): RemoteDeviceStatus.Pump =
    RemoteDeviceStatus.Pump(
        clock = clock,
        reservoir = reservoir,
        reservoirDisplayOverride = reservoirDisplayOverride,
        battery = RemoteDeviceStatus.Pump.Battery(battery?.percent, battery?.voltage),
        status = RemoteDeviceStatus.Pump.Status(status?.status, status?.timestamp),
        extended = extended
    )

internal fun RemoteDeviceStatus.OpenAps.toNSDeviceStatusOpenAps(): NSDeviceStatus.OpenAps =
    NSDeviceStatus.OpenAps(
        suggested = suggested,
        enacted = enacted,
        iob = iob
    )

internal fun NSDeviceStatus.OpenAps.toRemoteDeviceStatusOpenAps(): RemoteDeviceStatus.OpenAps =
    RemoteDeviceStatus.OpenAps(
        suggested = suggested,
        enacted = enacted,
        iob = iob
    )
