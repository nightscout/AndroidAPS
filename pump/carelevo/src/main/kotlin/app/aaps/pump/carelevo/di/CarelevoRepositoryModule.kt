package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import app.aaps.pump.carelevo.data.repository.CarelevoAlarmInfoLocalRepositoryImpl
import app.aaps.pump.carelevo.data.repository.CarelevoInfusionInfoRepositoryImpl
import app.aaps.pump.carelevo.data.repository.CarelevoPatchInfoRepositoryImpl
import app.aaps.pump.carelevo.data.repository.CarelevoUserSettingInfoRepositoryImpl
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class CarelevoRepositoryModule {

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
