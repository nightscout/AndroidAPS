package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.objects.aps.DetermineBasalResult
import dagger.android.HasAndroidInjector
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json

fun app.aaps.database.entities.APSResult.fromDb(injector: HasAndroidInjector): APSResult =
    when (algorithm) {
        app.aaps.database.entities.APSResult.Algorithm.AMA,
        app.aaps.database.entities.APSResult.Algorithm.SMB      ->
            DetermineBasalResult(injector, Json.decodeFromString(this.resultJson)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = this.glucoseStatusJson?.let { Json.decodeFromString(it) }
                result.currentTemp = this.currentTempJson?.let { Json.decodeFromString(it) }
                result.iobData = this.iobDataJson?.let { Json.decodeFromString(it) }
                result.oapsProfile = this.profileJson?.let { Json.decodeFromString(it) }
                result.mealData = this.mealDataJson?.let { Json.decodeFromString(it) }
                result.autosensResult = this.autosensDataJson?.let { Json.decodeFromString(it) }
            }

        app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF ->
            DetermineBasalResult(injector, Json.decodeFromString(this.resultJson)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = this.glucoseStatusJson?.let { Json.decodeFromString(it) }
                result.currentTemp = this.currentTempJson?.let { Json.decodeFromString(it) }
                result.iobData = this.iobDataJson?.let { Json.decodeFromString(it) }
                result.oapsProfileAutoIsf = this.profileJson?.let { Json.decodeFromString(it) }
                result.mealData = this.mealDataJson?.let { Json.decodeFromString(it) }
                result.autosensResult = this.autosensDataJson?.let { Json.decodeFromString(it) }
            }

        else                                                    -> error("Unsupported")
    }

@OptIn(ExperimentalSerializationApi::class)
fun APSResult.toDb(): app.aaps.database.entities.APSResult =
    when (algorithm) {
        APSResult.Algorithm.AMA,
        APSResult.Algorithm.SMB      ->
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { Json.encodeToString(GlucoseStatus.serializer(), it) },
                currentTempJson = this.currentTemp?.let { Json.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { Json.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfile?.let { Json.encodeToString(OapsProfile.serializer(), it) },
                mealDataJson = this.mealData?.let { Json.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { Json.encodeToString(AutosensResult.serializer(), it) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )

        APSResult.Algorithm.AUTO_ISF ->
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { Json.encodeToString(GlucoseStatus.serializer(), it) },
                currentTempJson = this.currentTemp?.let { Json.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { Json.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfileAutoIsf?.let { Json.encodeToString(OapsProfileAutoIsf.serializer(), it) },
                mealDataJson = this.mealData?.let { Json.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { Json.encodeToString(AutosensResult.serializer(), it) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )

        else                         -> error("Unsupported")
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