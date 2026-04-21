package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoAlarmInfoLocalRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoBasalRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoBolusRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoInfusionInfoRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoPatchInfoRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoPatchRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.repository.CarelevoUserSettingInfoRepositoryImpl
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository

@Module
@InstallIn(SingletonComponent::class)
class CarelevoRepositoryModule {

    @Provides
    fun provideCarelevoBasalRepository(
        carelevoBtBasalRemoteDataSource: CarelevoBtBasalRemoteDataSource
    ): CarelevoBasalRepository {
        return CarelevoBasalRepositoryImpl(
            carelevoBtBasalRemoteDataSource
        )
    }

    @Provides
    fun provideCarelevoBolusRepository(
        carelevoBtBolusRemoteDataSource: CarelevoBtBolusRemoteDataSource
    ): CarelevoBolusRepository {
        return CarelevoBolusRepositoryImpl(
            carelevoBtBolusRemoteDataSource
        )
    }

    @Provides
    fun provideCarelevoPatchRepository(
        carelevoBtPatchRemoteDataSource: CarelevoBtPatchRemoteDataSource
    ): CarelevoPatchRepository {
        return CarelevoPatchRepositoryImpl(
            carelevoBtPatchRemoteDataSource
        )
    }

    @Provides
    fun provideCarelevoInfusionInfoRepository(
        carelevoInfusionInfoDataSource: CarelevoInfusionInfoDataSource
    ): CarelevoInfusionInfoRepository {
        return CarelevoInfusionInfoRepositoryImpl(
            carelevoInfusionInfoDataSource
        )
    }

    @Provides
    fun provideCarelevoPatchInfoRepository(
        carelevoPatchInfoDataSource: CarelevoPatchInfoDataSource
    ): CarelevoPatchInfoRepository {
        return CarelevoPatchInfoRepositoryImpl(
            carelevoPatchInfoDataSource
        )
    }

    @Provides
    fun provideCarelevoUserSettingInfoRepository(
        carelevoUserSettingInfoDataSource: CarelevoUserSettingInfoDataSource
    ): CarelevoUserSettingInfoRepository {
        return CarelevoUserSettingInfoRepositoryImpl(
            carelevoUserSettingInfoDataSource
        )
    }

    @Provides
    fun provideCarelevoAlarmInfoLocalRepository(
        carelevoAlarmInfoLocalDataSource: CarelevoAlarmInfoLocalDataSource
    ): CarelevoAlarmInfoRepository {
        return CarelevoAlarmInfoLocalRepositoryImpl(carelevoAlarmInfoLocalDataSource)
    }
}
