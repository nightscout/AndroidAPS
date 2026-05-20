package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import app.aaps.pump.carelevo.data.dao.CarelevoInfusionInfoDao
import app.aaps.pump.carelevo.data.dao.CarelevoPatchInfoDao
import app.aaps.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSource
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSource
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSourceImpl
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSource
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSourceImpl
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
