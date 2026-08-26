package app.aaps.di.metro

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Builds the empty [PumpLeaves] for a follower build. See the `src/withPumps` copy. */
@Module
@InstallIn(SingletonComponent::class)
class PumpLeavesModule {

    @Provides
    @Singleton
    fun providePumpLeaves(): PumpLeaves = PumpLeaves()
}
