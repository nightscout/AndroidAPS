package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Pure-JVM round-trip coverage for [CarelevoUserSettingInfoDaoImpl].
 *
 * The DAO persists a single JSON blob under [PrefEnvConfig.USER_SETTING_INFO] via [SP] and caches
 * the parsed entity in an internal `BehaviorSubject`. We back the mocked [SP] with an in-memory map
 * (the "fake preferences" approach) so save/load/update/delete really round-trip through the real
 * gson used by production code, and we can spawn a fresh DAO instance sharing the same store to
 * exercise the cold-read (cache-miss) branches.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoUserSettingInfoDaoImplTest {

    @Mock lateinit var sp: SP

    private val store = mutableMapOf<String, String>()

    private lateinit var sut: CarelevoUserSettingInfoDaoImpl

    private fun sampleSetting(
        createdAt: String = "2026-07-16T10:00:00",
        updatedAt: String = "2026-07-16T11:00:00",
        lowInsulinNoticeAmount: Int? = 10,
        maxBasalSpeed: Double? = 2.5,
        maxBolusDose: Double? = 15.0
    ): CarelevoUserSettingInfoEntity =
        CarelevoUserSettingInfoEntity(
            createdAt = createdAt,
            updatedAt = updatedAt,
            lowInsulinNoticeAmount = lowInsulinNoticeAmount,
            maxBasalSpeed = maxBasalSpeed,
            maxBolusDose = maxBolusDose,
            needLowInsulinNoticeAmountSyncPatch = true,
            needMaxBasalSpeedSyncPatch = false,
            needMaxBolusDoseSyncPatch = true
        )

    private fun storeJson(entity: CarelevoUserSettingInfoEntity) {
        store[PrefEnvConfig.USER_SETTING_INFO] = CarelevoGsonHelper.sharedGson().toJson(entity)
    }

    @BeforeEach
    fun setUp() {
        // Fake preferences backed by [store]: getString reads the map (falling back to the default),
        // putString writes it, remove deletes it.
        whenever(sp.getString(any<String>(), any<String>())).thenAnswer { inv ->
            val key = inv.getArgument<String>(0)
            val def = inv.getArgument<String>(1)
            store[key] ?: def
        }
        doAnswer { inv ->
            store[inv.getArgument<String>(0)] = inv.getArgument<String>(1)
            null
        }.whenever(sp).putString(any<String>(), any<String>())
        doAnswer { inv ->
            store.remove(inv.getArgument<String>(0))
            null
        }.whenever(sp).remove(any<String>())

        sut = CarelevoUserSettingInfoDaoImpl(sp)
    }

    // region getUserSetting (Observable)

    @Test
    fun `getUserSetting emits the stored entity when preferences hold valid JSON`() {
        storeJson(sampleSetting())

        val emitted = sut.getUserSetting().blockingFirst()

        assertThat(emitted.isPresent).isTrue()
        assertThat(emitted.get()).isEqualTo(sampleSetting())
    }

    @Test
    fun `getUserSetting emits empty when the preference is absent`() {
        val emitted = sut.getUserSetting().blockingFirst()

        assertThat(emitted.isPresent).isFalse()
    }

    @Test
    fun `getUserSetting emits empty when the stored JSON is malformed`() {
        store[PrefEnvConfig.USER_SETTING_INFO] = "{{{not-valid-json"

        val emitted = sut.getUserSetting().blockingFirst()

        assertThat(emitted.isPresent).isFalse()
    }

    @Test
    fun `getUserSetting reads preferences only once and serves the cached subject afterwards`() {
        storeJson(sampleSetting())

        sut.getUserSetting().blockingFirst()
        sut.getUserSetting().blockingFirst()

        verify(sp, times(1)).getString(any<String>(), any<String>())
    }

    // endregion

    // region getUserSettingBySync

    @Test
    fun `getUserSettingBySync returns the stored entity when preferences hold valid JSON`() {
        storeJson(sampleSetting())

        assertThat(sut.getUserSettingBySync()).isEqualTo(sampleSetting())
    }

    @Test
    fun `getUserSettingBySync returns null when the preference is absent`() {
        assertThat(sut.getUserSettingBySync()).isNull()
    }

    @Test
    fun `getUserSettingBySync returns null when the stored JSON is malformed`() {
        store[PrefEnvConfig.USER_SETTING_INFO] = "@@not-json@@"

        assertThat(sut.getUserSettingBySync()).isNull()
    }

    @Test
    fun `getUserSettingBySync reads preferences only once and serves the cached value afterwards`() {
        storeJson(sampleSetting())

        sut.getUserSettingBySync()
        sut.getUserSettingBySync()

        verify(sp, times(1)).getString(any<String>(), any<String>())
    }

    // endregion

    // region updateUserSetting

    @Test
    fun `updateUserSetting persists the JSON blob and returns true`() {
        val setting = sampleSetting()

        assertThat(sut.updateUserSetting(setting)).isTrue()

        verify(sp).putString(any<String>(), any<String>())
        assertThat(store[PrefEnvConfig.USER_SETTING_INFO])
            .isEqualTo(CarelevoGsonHelper.sharedGson().toJson(setting))
    }

    @Test
    fun `updateUserSetting refreshes the cache so later reads do not touch preferences`() {
        val setting = sampleSetting()

        assertThat(sut.updateUserSetting(setting)).isTrue()
        assertThat(sut.getUserSettingBySync()).isEqualTo(setting)
        assertThat(sut.getUserSetting().blockingFirst().get()).isEqualTo(setting)

        verify(sp, never()).getString(any<String>(), any<String>())
    }

    @Test
    fun `updateUserSetting returns false when persisting throws`() {
        doThrow(RuntimeException("disk full")).whenever(sp).putString(any<String>(), any<String>())

        assertThat(sut.updateUserSetting(sampleSetting())).isFalse()
    }

    // endregion

    // region deleteUserSetting

    @Test
    fun `deleteUserSetting removes the preference and returns true`() {
        sut.updateUserSetting(sampleSetting())

        assertThat(sut.deleteUserSetting()).isTrue()

        verify(sp).remove(any<String>())
        assertThat(store).doesNotContainKey(PrefEnvConfig.USER_SETTING_INFO)
    }

    @Test
    fun `deleteUserSetting clears the cache so later reads return null without touching preferences`() {
        sut.updateUserSetting(sampleSetting())

        assertThat(sut.deleteUserSetting()).isTrue()
        assertThat(sut.getUserSettingBySync()).isNull()

        verify(sp, never()).getString(any<String>(), any<String>())
    }

    @Test
    fun `deleteUserSetting returns false when removal throws`() {
        doThrow(RuntimeException("io error")).whenever(sp).remove(any<String>())

        assertThat(sut.deleteUserSetting()).isFalse()
    }

    // endregion

    // region round-trip across DAO instances (cold read / cache-miss branches)

    @Test
    fun `saved setting is loadable by a fresh DAO instance via getUserSettingBySync`() {
        val setting = sampleSetting()
        sut.updateUserSetting(setting)

        val fresh = CarelevoUserSettingInfoDaoImpl(sp)

        assertThat(fresh.getUserSettingBySync()).isEqualTo(setting)
    }

    @Test
    fun `saved setting is loadable by a fresh DAO instance via getUserSetting observable`() {
        val setting = sampleSetting(maxBolusDose = null)
        sut.updateUserSetting(setting)

        val fresh = CarelevoUserSettingInfoDaoImpl(sp)

        assertThat(fresh.getUserSetting().blockingFirst().get()).isEqualTo(setting)
    }

    @Test
    fun `deleted setting reads back as null on a fresh DAO instance`() {
        sut.updateUserSetting(sampleSetting())
        sut.deleteUserSetting()

        val fresh = CarelevoUserSettingInfoDaoImpl(sp)

        assertThat(fresh.getUserSettingBySync()).isNull()
        assertThat(fresh.getUserSetting().blockingFirst().isPresent).isFalse()
    }

    // endregion
}
