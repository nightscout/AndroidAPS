package app.aaps.pump.aaruy.di

import android.content.Context
import app.aaps.pump.aaruy.compose.AaruyBleReadyCheck
import app.aaps.pump.aaruy.compose.AndroidAaruyBleReadyCheck
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.buildAaruyHistoryDatabase
import app.aaps.pump.aaruy.services.AaruyEmulatorTriggers
import app.aaps.pump.aaruy.services.AndroidAaruyEmulatorTriggers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Android has every port of Aaruy (see [AaruyRegistration]), so Aaruy is a pump the user can select here. */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [AaruyRegistration::class])
object AaruyAndroidRegistration {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): AaruyHistoryDatabase = buildAaruyHistoryDatabase(context)

    @Provides
    fun provideEmulatorTriggers(triggers: AndroidAaruyEmulatorTriggers): AaruyEmulatorTriggers = triggers

    @Provides
    fun provideBleReadyCheck(check: AndroidAaruyBleReadyCheck): AaruyBleReadyCheck = check
}
