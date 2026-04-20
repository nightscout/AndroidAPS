package info.nightscout.androidaps.plugins.pump.carelevo.di

import app.aaps.core.interfaces.logging.AAPSLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoInfusionInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoPatchInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSourceImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider

@Module
@InstallIn(SingletonComponent::class)
class CarelevoDataSourceModule {

    @Provides
    fun provideCarelevoBtBasalRemoteDataSource(
        carelevoBleController: CarelevoBleController,
        carelevoProtocolParserProvider: CarelevoProtocolParserProvider
    ): CarelevoBtBasalRemoteDataSource {
        return CarelevoBtBasalRemoteDataSourceImpl(
            carelevoBleController,
            carelevoProtocolParserProvider
        )
    }

    @Provides
    fun provideCarelevoBolusRemoteDataSource(
        carelevoBleController: CarelevoBleController,
        carelevoProtocolParserProvider: CarelevoProtocolParserProvider
    ): CarelevoBtBolusRemoteDataSource {
        return CarelevoBtBolusRemoteDataSourceImpl(
            carelevoBleController,
            carelevoProtocolParserProvider
        )
    }

    @Provides
    fun provideCarelevoBtPatchRemoteDataSource(
        carelevoBleController: CarelevoBleController,
        carelevoProtocolParserProvider: CarelevoProtocolParserProvider
    ): CarelevoBtPatchRemoteDataSource {
        return CarelevoBtPatchRemoteDataSourceImpl(
            carelevoBleController,
            carelevoProtocolParserProvider
        )
    }

    @Provides
    fun provideCarelevoInfusionInfoDataSource(
        carelevoInfusionInfoDao: CarelevoInfusionInfoDao
    ): CarelevoInfusionInfoDataSource {
        return CarelevoInfusionInfoDataSourceImpl(
            carelevoInfusionInfoDao
        )
    }

    @Provides
    fun provideCarelevoPatchInfoDataSource(
        carelevoPatchInfoDao: CarelevoPatchInfoDao
    ): CarelevoPatchInfoDataSource {
        return CarelevoPatchInfoDataSourceImpl(
            carelevoPatchInfoDao
        )
    }

    @Provides
    fun provideCarelevoUserSettingInfoDataSource(
        carelevoUserSettingInfoDao: CarelevoUserSettingInfoDao
    ): CarelevoUserSettingInfoDataSource {
        return CarelevoUserSettingInfoDataSourceImpl(
            carelevoUserSettingInfoDao
        )
    }

    @Provides
    fun provideCarelevoAlarmInfoLocalDataSource(
        dao: CarelevoAlarmInfoDao
    ): CarelevoAlarmInfoLocalDataSource {
        return CarelevoAlarmInfoLocalDataSourceImpl(dao)
    }
}
