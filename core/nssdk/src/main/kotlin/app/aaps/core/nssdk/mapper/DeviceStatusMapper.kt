package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.remotemodel.RemoteDeviceStatus
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.serialization.json.Json

fun NSDeviceStatus.convertToRemoteAndBack(): NSDeviceStatus =
    toRemoteDeviceStatus().toNSDeviceStatus()

fun String.toNSDeviceStatus(): NSDeviceStatus =
    Gson().fromJson(this, RemoteDeviceStatus::class.java).toNSDeviceStatus()

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
        openaps = openaps?.toNSDeviceStatusOpenAps(),
        configuration = configuration?.toNSDeviceStatusConfiguration()
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
        openaps = openaps?.toRemoteDeviceStatusOpenAps(),
        configuration = configuration?.toRemoteDeviceStatusConfiguration()
    )

internal fun RemoteDeviceStatus.Pump.toNSDeviceStatusPump(): NSDeviceStatus.Pump =
    NSDeviceStatus.Pump(
        clock = clock,
        reservoir = reservoir,
        reservoirDisplayOverride = reservoirDisplayOverride,
        battery = NSDeviceStatus.Pump.Battery(battery?.percent, battery?.voltage),
        status = NSDeviceStatus.Pump.Status(status?.status, status?.timestamp),
        extended = extended?.let { Json.decodeFromString(it.toString()) }
    )

internal fun NSDeviceStatus.Pump.toRemoteDeviceStatusPump(): RemoteDeviceStatus.Pump =
    RemoteDeviceStatus.Pump(
        clock = clock,
        reservoir = reservoir,
        reservoirDisplayOverride = reservoirDisplayOverride,
        battery = RemoteDeviceStatus.Pump.Battery(battery?.percent, battery?.voltage),
        status = RemoteDeviceStatus.Pump.Status(status?.status, status?.timestamp),
        extended = extended?.let { JsonParser.parseString(it.toString()).asJsonObject }
    )

internal fun RemoteDeviceStatus.OpenAps.toNSDeviceStatusOpenAps(): NSDeviceStatus.OpenAps =
    NSDeviceStatus.OpenAps(
        suggested = suggested?.let { Json.decodeFromString(it.toString()) },
        enacted = enacted?.let { Json.decodeFromString(it.toString()) },
        iob = iob?.let { Json.decodeFromString(it.toString()) }
    )

internal fun NSDeviceStatus.OpenAps.toRemoteDeviceStatusOpenAps(): RemoteDeviceStatus.OpenAps =
    RemoteDeviceStatus.OpenAps(
        suggested = suggested?.let { JsonParser.parseString(it.toString()).asJsonObject },
        enacted = enacted?.let { JsonParser.parseString(it.toString()).asJsonObject },
        iob = iob?.let { JsonParser.parseString(it.toString()).asJsonObject }
    )

internal fun RemoteDeviceStatus.Configuration.toNSDeviceStatusConfiguration(): NSDeviceStatus.Configuration =
    NSDeviceStatus.Configuration(
        pump = pump,
        version = version,
        insulin = insulin,
        aps = aps,
        sensitivity = sensitivity,
        smoothing = smoothing,
        insulinConfiguration = insulinConfiguration?.let { Json.decodeFromString(it.toString()) },
        apsConfiguration = apsConfiguration?.let { Json.decodeFromString(it.toString()) },
        sensitivityConfiguration = sensitivityConfiguration?.let { Json.decodeFromString(it.toString()) },
        overviewConfiguration = overviewConfiguration?.let { Json.decodeFromString(it.toString()) },
        safetyConfiguration = safetyConfiguration?.let { Json.decodeFromString(it.toString()) }
    )

internal fun NSDeviceStatus.Configuration.toRemoteDeviceStatusConfiguration(): RemoteDeviceStatus.Configuration =
    RemoteDeviceStatus.Configuration(
        pump = pump,
        version = version,
        insulin = insulin,
        aps = aps,
        sensitivity = sensitivity,
        smoothing = smoothing,
        insulinConfiguration = insulinConfiguration?.let { JsonParser.parseString(it.toString()).asJsonObject },
        apsConfiguration = apsConfiguration?.let { JsonParser.parseString(it.toString()).asJsonObject },
        sensitivityConfiguration = sensitivityConfiguration?.let { JsonParser.parseString(it.toString()).asJsonObject },
        overviewConfiguration = overviewConfiguration?.let { JsonParser.parseString(it.toString()).asJsonObject },
        safetyConfiguration = safetyConfiguration?.let { JsonParser.parseString(it.toString()).asJsonObject }
    )

