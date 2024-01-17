package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsAutosensData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.objects.aps.DetermineBasalResult
import dagger.android.HasAndroidInjector
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json

fun app.aaps.database.entities.APSResult.fromDb(injector: HasAndroidInjector): APSResult =
    when (algorithm) {
        app.aaps.database.entities.APSResult.Algorithm.AMA,
        app.aaps.database.entities.APSResult.Algorithm.SMB ->
            DetermineBasalResult(injector, Json.decodeFromString(this.resultJson), this.algorithm.fromDb()).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = Json.decodeFromString(this.glucoseStatusJson)
                result.currentTemp = Json.decodeFromString(CurrentTemp.serializer(), this.currentTempJson)
                result.iobData = Json.decodeFromString(this.iobDataJson)
                result.oapsProfile = Json.decodeFromString(this.profileJson)
                result.mealData = Json.decodeFromString(this.mealDataJson)
                result.oapsAutosensData = this.autosensDataJson?.let { autosensDataJson -> Json.decodeFromString(autosensDataJson) }
            }

        else                                               -> error("Unsupported")
    }

@OptIn(ExperimentalSerializationApi::class)
fun APSResult.toDb(): app.aaps.database.entities.APSResult =
    when (algorithm) {
        APSResult.Algorithm.AMA,
        APSResult.Algorithm.SMB ->
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = Json.encodeToString(GlucoseStatus.serializer(), this.glucoseStatus ?: error("Not provided")),
                currentTempJson = Json.encodeToString(CurrentTemp.serializer(), this.currentTemp ?: error("Not provided")),
                iobDataJson = Json.encodeToString(ArraySerializer(IobTotal.serializer()), this.iobData ?: error("Not provided")),
                profileJson = Json.encodeToString(OapsProfile.serializer(), this.oapsProfile ?: error("Not provided")),
                mealDataJson = Json.encodeToString(MealData.serializer(), this.mealData ?: error("Not provided")),
                autosensDataJson = this.oapsAutosensData?.let { oapsAutosensData -> Json.encodeToString(OapsAutosensData.serializer(), oapsAutosensData) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )

        else                    -> error("Unsupported")
    }

fun app.aaps.database.entities.APSResult.Algorithm.fromDb(): APSResult.Algorithm =
    when (this) {
        app.aaps.database.entities.APSResult.Algorithm.AMA      -> APSResult.Algorithm.AMA
        app.aaps.database.entities.APSResult.Algorithm.SMB      -> APSResult.Algorithm.SMB
        app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF -> APSResult.Algorithm.AUTO_ISF
        else                                                    -> error("Unsupported")
    }

fun APSResult.Algorithm.toDb(): app.aaps.database.entities.APSResult.Algorithm =
    when (this) {
        APSResult.Algorithm.AMA      -> app.aaps.database.entities.APSResult.Algorithm.AMA
        APSResult.Algorithm.SMB      -> app.aaps.database.entities.APSResult.Algorithm.SMB
        APSResult.Algorithm.AUTO_ISF -> app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF
        else                         -> error("Unsupported")
    }