package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
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
 * Pure-JVM round-trip coverage for [CarelevoPatchInfoDaoImpl].
 *
 * The DAO persists a single JSON blob under [PrefEnvConfig.PATCH_INFO] via [SP] and caches the
 * parsed entity in an internal `BehaviorSubject`. We back the mocked [SP] with an in-memory map (the
 * "fake preferences" approach) so save/load/update/delete really round-trip through the real gson
 * used by production code, and we spawn a fresh DAO instance sharing the same store to exercise the
 * cold-read (cache-miss) branches.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchInfoDaoImplTest {

    @Mock lateinit var sp: SP

    private val store = mutableMapOf<String, String>()

    private lateinit var sut: CarelevoPatchInfoDaoImpl

    private fun samplePatch(
        address: String = "AA:BB:CC:DD:EE:FF",
        createdAt: String = "2026-07-16T10:00:00",
        updatedAt: String = "2026-07-16T11:00:00",
        insulinRemain: Double? = 60.0,
        mode: Int? = 1
    ): CarelevoPatchInfoEntity =
        CarelevoPatchInfoEntity(
            address = address,
            createdAt = createdAt,
            updatedAt = updatedAt,
            manufactureNumber = "CARELEVO-TEST-001",
            firmwareVersion = "1.2.3",
            insulinAmount = 200,
            insulinRemain = insulinRemain,
            isConnected = true,
            mode = mode,
            bolusActionSeq = 3,
            pumpState = 0
        )

    private fun storeJson(entity: CarelevoPatchInfoEntity) {
        store[PrefEnvConfig.PATCH_INFO] = CarelevoGsonHelper.sharedGson().toJson(entity)
    }

    @BeforeEach
    fun setUp() {
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

        sut = CarelevoPatchInfoDaoImpl(sp)
    }

    // region getPatchInfo (Observable)

    @Test
    fun `getPatchInfo emits the stored entity when preferences hold valid JSON`() {
        storeJson(samplePatch())

        val emitted = sut.getPatchInfo().blockingFirst()

        assertThat(emitted.isPresent).isTrue()
        assertThat(emitted.get()).isEqualTo(samplePatch())
    }

    @Test
    fun `getPatchInfo emits empty when the preference is absent`() {
        val emitted = sut.getPatchInfo().blockingFirst()

        assertThat(emitted.isPresent).isFalse()
    }

    @Test
    fun `getPatchInfo emits empty when the stored JSON is malformed`() {
        store[PrefEnvConfig.PATCH_INFO] = "{{{not-valid-json"

        val emitted = sut.getPatchInfo().blockingFirst()

        assertThat(emitted.isPresent).isFalse()
    }

    @Test
    fun `getPatchInfo reads preferences only once and serves the cached subject afterwards`() {
        storeJson(samplePatch())

        sut.getPatchInfo().blockingFirst()
        sut.getPatchInfo().blockingFirst()

        verify(sp, times(1)).getString(any<String>(), any<String>())
    }

    // endregion

    // region getPatchInfoBySync

    @Test
    fun `getPatchInfoBySync returns the stored entity when preferences hold valid JSON`() {
        storeJson(samplePatch())

        assertThat(sut.getPatchInfoBySync()).isEqualTo(samplePatch())
    }

    @Test
    fun `getPatchInfoBySync returns null when the preference is absent`() {
        assertThat(sut.getPatchInfoBySync()).isNull()
    }

    @Test
    fun `getPatchInfoBySync returns null when the stored JSON is malformed`() {
        store[PrefEnvConfig.PATCH_INFO] = "@@not-json@@"

        assertThat(sut.getPatchInfoBySync()).isNull()
    }

    @Test
    fun `getPatchInfoBySync reads preferences only once and serves the cached value afterwards`() {
        storeJson(samplePatch())

        sut.getPatchInfoBySync()
        sut.getPatchInfoBySync()

        verify(sp, times(1)).getString(any<String>(), any<String>())
    }

    // endregion

    // region updatePatchInfo

    @Test
    fun `updatePatchInfo persists the JSON blob and returns true`() {
        val info = samplePatch()

        assertThat(sut.updatePatchInfo(info)).isTrue()

        verify(sp).putString(any<String>(), any<String>())
        assertThat(store[PrefEnvConfig.PATCH_INFO])
            .isEqualTo(CarelevoGsonHelper.sharedGson().toJson(info))
    }

    @Test
    fun `updatePatchInfo refreshes the cache so later reads do not touch preferences`() {
        val info = samplePatch()

        assertThat(sut.updatePatchInfo(info)).isTrue()
        assertThat(sut.getPatchInfoBySync()).isEqualTo(info)
        assertThat(sut.getPatchInfo().blockingFirst().get()).isEqualTo(info)

        verify(sp, never()).getString(any<String>(), any<String>())
    }

    @Test
    fun `updatePatchInfo returns false when persisting throws`() {
        doThrow(RuntimeException("disk full")).whenever(sp).putString(any<String>(), any<String>())

        assertThat(sut.updatePatchInfo(samplePatch())).isFalse()
    }

    // endregion

    // region deletePatchInfo

    @Test
    fun `deletePatchInfo removes the preference and returns true`() {
        sut.updatePatchInfo(samplePatch())

        assertThat(sut.deletePatchInfo()).isTrue()

        verify(sp).remove(any<String>())
        assertThat(store).doesNotContainKey(PrefEnvConfig.PATCH_INFO)
    }

    @Test
    fun `deletePatchInfo clears the cache so later reads return null without touching preferences`() {
        sut.updatePatchInfo(samplePatch())

        assertThat(sut.deletePatchInfo()).isTrue()
        assertThat(sut.getPatchInfoBySync()).isNull()

        verify(sp, never()).getString(any<String>(), any<String>())
    }

    @Test
    fun `deletePatchInfo returns false when removal throws`() {
        doThrow(RuntimeException("io error")).whenever(sp).remove(any<String>())

        assertThat(sut.deletePatchInfo()).isFalse()
    }

    // endregion

    // region round-trip across DAO instances (cold read / cache-miss branches)

    @Test
    fun `saved patch info is loadable by a fresh DAO instance via getPatchInfoBySync`() {
        val info = samplePatch()
        sut.updatePatchInfo(info)

        val fresh = CarelevoPatchInfoDaoImpl(sp)

        assertThat(fresh.getPatchInfoBySync()).isEqualTo(info)
    }

    @Test
    fun `saved patch info is loadable by a fresh DAO instance via getPatchInfo observable`() {
        val info = samplePatch(insulinRemain = null, mode = null)
        sut.updatePatchInfo(info)

        val fresh = CarelevoPatchInfoDaoImpl(sp)

        assertThat(fresh.getPatchInfo().blockingFirst().get()).isEqualTo(info)
    }

    @Test
    fun `deleted patch info reads back as null on a fresh DAO instance`() {
        sut.updatePatchInfo(samplePatch())
        sut.deletePatchInfo()

        val fresh = CarelevoPatchInfoDaoImpl(sp)

        assertThat(fresh.getPatchInfoBySync()).isNull()
        assertThat(fresh.getPatchInfo().blockingFirst().isPresent).isFalse()
    }

    // endregion
}
