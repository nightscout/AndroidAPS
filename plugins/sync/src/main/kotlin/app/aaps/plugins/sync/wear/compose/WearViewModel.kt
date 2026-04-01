package app.aaps.plugins.sync.wear.compose

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventMobileToWearWatchface
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CUSTOM_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfFile
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.CwfMetadataMap
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.shared.impl.weardata.JsonKeyValues
import app.aaps.shared.impl.weardata.JsonKeys
import app.aaps.shared.impl.weardata.ResFileMap
import app.aaps.shared.impl.weardata.ViewKeys
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@Immutable
data class WearUiState(
    val connectedDevice: String = "---",
    val isDeviceConnected: Boolean = false,
    val hasCustomWatchface: Boolean = false,
    val watchfaceName: String = "",
    val watchfaceImage: ImageBitmap? = null,
    val showInfos: Boolean = false,
    val cwfInfosState: CwfInfosState? = null,
    val showImportList: Boolean = false,
    val importItems: List<CwfImportItemState> = emptyList()
)

@Immutable
data class CwfInfosState(
    val title: String = "",
    val fileName: String = "",
    val author: String = "",
    val createdAt: String = "",
    val version: String = "",
    val isVersionOk: Boolean = false,
    val comment: String = "",
    val prefTitle: String = "",
    val preferences: List<CwfPrefItem> = emptyList(),
    val viewElements: List<CwfViewItem> = emptyList(),
    val watchfaceImage: ImageBitmap? = null
)

@Immutable
data class CwfPrefItem(val label: String, val isEnabled: Boolean)

@Immutable
data class CwfViewItem(val key: String, val comment: String)

@Immutable
data class CwfImportItemState(
    val cwfFile: CwfFile,
    val name: String,
    val fileName: String,
    val author: String,
    val createdAt: String,
    val version: String,
    val isVersionOk: Boolean,
    val prefCount: Int,
    val hasPrefAuthorization: Boolean,
    val watchfaceImage: ImageBitmap?
)

@HiltViewModel
@Stable
class WearViewModel @Inject constructor(
    private val wearPlugin: WearPlugin,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val fileListProvider: FileListProvider,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<WearUiState>
        field = MutableStateFlow(WearUiState())

    private var currentCwfData: CwfData? = null

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            wearPlugin.connectedDevice.collect { device ->
                uiState.update {
                    it.copy(
                        connectedDevice = device ?: rh.gs(R.string.no_watch_connected),
                        isDeviceConnected = device != null
                    )
                }
            }
        }
        viewModelScope.launch {
            wearPlugin.savedCustomWatchface.collect { cwfData ->
                currentCwfData = cwfData
                uiState.update {
                    it.copy(
                        hasCustomWatchface = cwfData != null,
                        watchfaceName = cwfData?.metadata?.get(CwfMetadataKey.CWF_NAME) ?: "",
                        watchfaceImage = cwfData?.let { cwf -> decodeWatchfaceImage(cwf) }
                    )
                }
            }
        }
        viewModelScope.launch {
            rxBus.toFlow(EventWearUpdateGui::class.java).collect { event ->
                if (event.exportFile) {
                    _toastEvent.emit(rh.gs(R.string.wear_new_custom_watchface_exported))
                } else {
                    event.customWatchfaceData?.let { cwfData ->
                        wearPlugin.updateSavedCustomWatchface(cwfData)
                    }
                }
            }
        }
    }

    fun requestCustomWatchface() {
        if (wearPlugin.savedCustomWatchface.value == null) {
            rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(false)))
        }
    }

    fun resendData() {
        rxBus.send(EventData.ActionResendData("WearScreen"))
    }

    fun openSettingsOnWear() {
        rxBus.send(EventMobileToWear(EventData.OpenSettings(dateUtil.now())))
    }

    fun exportCustomWatchface() {
        rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(true)))
    }

    fun showCwfInfos() {
        val cwfData = currentCwfData ?: return
        val metadata = cwfData.metadata
        val cwfAuthorization = preferences.get(BooleanKey.WearCustomWatchfaceAuthorization)

        val titleName = metadata[CwfMetadataKey.CWF_NAME] ?: ""
        val authorVersion = metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]
        val title = if (authorVersion != null) "$titleName ($authorVersion)" else titleName

        val fileName = metadata[CwfMetadataKey.CWF_FILENAME]?.let { "$it${ZipWatchfaceFormat.CWF_EXTENSION}" } ?: ""

        val prefItems = metadata
            .filter { it.key.isPref && (it.value.lowercase() == "true" || it.value.lowercase() == "false") }
            .map { (key, value) ->
                CwfPrefItem(
                    label = rh.gs(key.label),
                    isEnabled = value.lowercase().toBooleanStrictOrNull() ?: false
                )
            }

        val viewItems = listVisibleView(cwfData.json)

        val infosState = CwfInfosState(
            title = title,
            fileName = rh.gs(CwfMetadataKey.CWF_FILENAME.label, fileName),
            author = rh.gs(CwfMetadataKey.CWF_AUTHOR.label, metadata[CwfMetadataKey.CWF_AUTHOR] ?: ""),
            createdAt = rh.gs(CwfMetadataKey.CWF_CREATED_AT.label, metadata[CwfMetadataKey.CWF_CREATED_AT] ?: ""),
            version = rh.gs(CwfMetadataKey.CWF_VERSION.label, metadata[CwfMetadataKey.CWF_VERSION] ?: ""),
            isVersionOk = checkCustomVersion(metadata),
            comment = rh.gs(CwfMetadataKey.CWF_COMMENT.label, metadata[CwfMetadataKey.CWF_COMMENT] ?: ""),
            prefTitle = if (metadata.count { it.key.isPref } > 0)
                rh.gs(if (cwfAuthorization) R.string.cwf_infos_pref_locked else R.string.cwf_infos_pref_required)
            else "",
            preferences = prefItems,
            viewElements = viewItems,
            watchfaceImage = decodeWatchfaceImage(cwfData)
        )

        uiState.update { it.copy(showInfos = true, cwfInfosState = infosState) }
    }

    fun hideCwfInfos() {
        uiState.update { it.copy(showInfos = false) }
    }

    private fun decodeWatchfaceImage(cwfData: CwfData): ImageBitmap? {
        val resData = cwfData.resData[ResFileMap.CUSTOM_WATCHFACE.fileName] ?: return null
        return try {
            BitmapFactory.decodeByteArray(resData.value, 0, resData.value.size)?.asImageBitmap()
        } catch (e: Exception) {
            aapsLogger.debug(LTag.WEAR, "Failed to decode watchface image: ${e.message}")
            null
        }
    }

    private fun checkCustomVersion(metadata: CwfMetadataMap): Boolean {
        metadata[CwfMetadataKey.CWF_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(CUSTOM_VERSION)
            val metadataVer = versionCheckerUtils.versionDigits(version)
            return (currentAppVer.size >= 2) && (metadataVer.size >= 2) && (currentAppVer[0] >= metadataVer[0])
        }
        return false
    }

    private fun listVisibleView(jsonString: String): List<CwfViewItem> {
        return try {
            val json = JSONObject(jsonString)
            ViewKeys.entries.mapNotNull { viewKey ->
                try {
                    val jsonValue = json.optJSONObject(viewKey.key)
                    if (jsonValue != null) {
                        val visibility = jsonValue.optString(JsonKeys.VISIBILITY.key) == JsonKeyValues.VISIBLE.key
                        if (visibility)
                            CwfViewItem(key = "\"${viewKey.key}\":", comment = rh.gs(viewKey.comment))
                        else null
                    } else null
                } catch (_: Exception) {
                    aapsLogger.debug(LTag.WEAR, "Wrong key in json file: ${viewKey.key}")
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadWatchfaceFiles() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cwfAuthorization = preferences.get(BooleanKey.WearCustomWatchfaceAuthorization)
            val files = fileListProvider.listCustomWatchfaceFiles()
                .sortedBy { it.cwfData.metadata[CwfMetadataKey.CWF_NAME] }
            val items = files.map { cwfFile ->
                val metadata = cwfFile.cwfData.metadata
                val name = metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]?.let { av ->
                    rh.gs(CwfMetadataKey.CWF_AUTHOR_VERSION.label, metadata[CwfMetadataKey.CWF_NAME], av)
                } ?: rh.gs(CwfMetadataKey.CWF_NAME.label, metadata[CwfMetadataKey.CWF_NAME])
                val fileName = metadata[CwfMetadataKey.CWF_FILENAME]?.let { "$it.${ZipWatchfaceFormat.CWF_EXTENSION}" } ?: ""
                CwfImportItemState(
                    cwfFile = cwfFile,
                    name = name,
                    fileName = rh.gs(CwfMetadataKey.CWF_FILENAME.label, fileName),
                    author = rh.gs(CwfMetadataKey.CWF_AUTHOR.label, metadata[CwfMetadataKey.CWF_AUTHOR] ?: ""),
                    createdAt = rh.gs(CwfMetadataKey.CWF_CREATED_AT.label, metadata[CwfMetadataKey.CWF_CREATED_AT] ?: ""),
                    version = rh.gs(CwfMetadataKey.CWF_VERSION.label, metadata[CwfMetadataKey.CWF_VERSION] ?: ""),
                    isVersionOk = checkCustomVersion(metadata),
                    prefCount = metadata.count { it.key.isPref },
                    hasPrefAuthorization = cwfAuthorization,
                    watchfaceImage = decodeWatchfaceImage(cwfFile.cwfData)
                )
            }
            uiState.update { it.copy(showImportList = true, importItems = items) }
        }
    }

    fun selectWatchface(cwfFile: CwfFile) {
        val metadata = cwfFile.cwfData.metadata
        preferences.put(StringNonKey.WearCwfWatchfaceName, metadata[CwfMetadataKey.CWF_NAME] ?: "")
        preferences.put(StringNonKey.WearCwfAuthorVersion, metadata[CwfMetadataKey.CWF_AUTHOR_VERSION] ?: "")
        preferences.put(StringNonKey.WearCwfFileName, metadata[CwfMetadataKey.CWF_FILENAME] ?: "")
        rxBus.send(EventMobileToWearWatchface(cwfFile.zipByteArray))
        uiState.update { it.copy(showImportList = false, importItems = emptyList()) }
    }

    fun hideImportList() {
        uiState.update { it.copy(showImportList = false, importItems = emptyList()) }
    }
}
