package info.nightscout.androidaps.skins

import info.nightscout.androidaps.R
import info.nightscout.androidaps.di.SkinsModule
import info.nightscout.shared.sharedPreferences.SP
import okhttp3.internal.toImmutableMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinProvider @Inject constructor(
    val sp: SP,
    @SkinsModule.Skin val allSkins: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards SkinInterface>
) {

    fun activeSkin(): SkinInterface =
        list.firstOrNull { it.javaClass.name == sp.getString(R.string.key_skin, "") }
            ?: list.first()

    val list: List<SkinInterface>
        get() = allSkins.toImmutableMap().toList().sortedBy { it.first }.map { it.second }
}
