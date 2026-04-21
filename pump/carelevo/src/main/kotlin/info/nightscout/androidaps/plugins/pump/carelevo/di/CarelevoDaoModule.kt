package info.nightscout.androidaps.plugins.pump.carelevo.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoAlarmInfoDaoImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoInfusionInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoInfusionInfoDaoImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoPatchInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoPatchInfoDaoImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoUserSettingInfoDaoImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CarelevoDaoModule {

    @Provides
    @Singleton
    fun provideCarelevoInfusionInfoDao(
        prefManager: SP
    ): CarelevoInfusionInfoDao {
        return CarelevoInfusionInfoDaoImpl(
            prefManager
        )
    }

    @Provides
    @Singleton
    fun provideCarelevoPatchInfoDao(
        prefManager: SP
    ): CarelevoPatchInfoDao {
        return CarelevoPatchInfoDaoImpl(
            prefManager
        )
    }

    @Provides
    @Singleton
    fun provideCarelevoUserSettingInfoDao(
        prefManager: SP
    ): CarelevoUserSettingInfoDao {
        return CarelevoUserSettingInfoDaoImpl(
            prefManager
        )
    }

    @Provides
    @Singleton
    fun provideCarelevoAlarmInfoDao(
        prefManager: SP
    ): CarelevoAlarmInfoDao {
        return CarelevoAlarmInfoDaoImpl(
            prefManager
        )
    }
}
