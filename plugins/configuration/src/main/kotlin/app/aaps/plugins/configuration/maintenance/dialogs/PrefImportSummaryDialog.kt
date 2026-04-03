package app.aaps.plugins.configuration.maintenance.dialogs

import android.app.Dialog
import android.content.Context
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefImportSummaryDialog @Inject constructor(
    private val preferences: Preferences,
    private val rxBus: RxBus
) {

    private data class DetailItem(val label: String, val value: String, val info: String)

    /**
     * A custom owner class that provides the necessary platform owners for a ComposeView
     * hosted in a custom Dialog.
     */
    private class ComposeDialogOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val _viewModelStore = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore
            get() = _viewModelStore

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            _viewModelStore.clear()
        }
    }

    fun showSummary(
        context: Context,
        importOk: Boolean,
        importPossible: Boolean,
        prefs: Prefs,
        ok: (() -> Unit)?,
        cancel: (() -> Unit)? = null
    ) {
        @StringRes val messageRes: Int = if (importOk) R.string.check_preferences_before_import else {
            if (importPossible) R.string.check_preferences_dangerous_import else R.string.check_preferences_cannot_import
        }

        @DrawableRes val headerIcon: Int = if (importOk) R.drawable.ic_header_import else {
            if (importPossible) app.aaps.core.ui.R.drawable.ic_header_warning else R.drawable.ic_header_error
        }

        val dialog = Dialog(context)
        val owner = ComposeDialogOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences
                ) {
                    AapsTheme {
                        ImportSummaryDialog(
                            title = context.getString(app.aaps.core.ui.R.string.import_setting),
                            message = context.getString(messageRes),
                            headerIcon = headerIcon,
                            importOk = importOk,
                            importPossible = importPossible,
                            prefs = prefs,
                            onImport = {
                                dialog.dismiss()
                                SystemClock.sleep(100)
                                if (ok != null) runOnUiThread { ok() }
                            },
                            onCancel = {
                                dialog.dismiss()
                                SystemClock.sleep(100)
                                if (cancel != null) runOnUiThread { cancel() }
                            }
                        )
                    }
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { owner.destroy() }
        dialog.show()
    }

    @Composable
    private fun ImportSummaryDialog(
        title: String,
        message: String,
        @DrawableRes headerIcon: Int,
        importOk: Boolean,
        importPossible: Boolean,
        prefs: Prefs,
        onImport: () -> Unit,
        onCancel: () -> Unit
    ) {
        val context = LocalContext.current
        var showDetailsDialog by remember { mutableStateOf(false) }

        val details = remember {
            mutableListOf<DetailItem>().apply {
                prefs.metadata.forEach { (metaKey, metaEntry) ->
                    if (metaEntry.info != null) {
                        add(
                            DetailItem(
                                label = context.getString(metaKey.label),
                                value = metaEntry.value,
                                info = metaEntry.info!!
                            )
                        )
                    }
                }
            }
        }

        val containerColor = when {
            importOk       -> MaterialTheme.colorScheme.surface
            importPossible -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else           -> MaterialTheme.colorScheme.errorContainer
        }

        AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(
                    painter = painterResource(id = headerIcon),
                    contentDescription = null,
                    tint = when {
                        importOk       -> AapsTheme.generalColors.statusNormal
                        importPossible -> AapsTheme.generalColors.statusWarning
                        else           -> MaterialTheme.colorScheme.error
                    }
                )
            },
            title = {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        items(prefs.metadata.entries.toList()) { (metaKey, metaEntry) ->
                            ImportSummaryItem(
                                metaKey = metaKey,
                                metaEntry = metaEntry,
                            )
                        }
                    }

                    if (details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showDetailsDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.check_preferences_details_btn))
                        }
                    }
                }
            },
            confirmButton = {
                if (importPossible) {
                    TextButton(onClick = onImport) {
                        Text(
                            stringResource(
                                if (importOk) R.string.check_preferences_import_btn
                                else R.string.check_preferences_import_anyway_btn
                            )
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = containerColor,
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )

        if (showDetailsDialog) {
            DetailsDialog(
                details = details,
                onDismiss = { showDetailsDialog = false }
            )
        }
    }

    @Composable
    private fun ImportSummaryItem(
        metaKey: PrefsMetadataKey,
        metaEntry: PrefMetadata
    ) {
        val context = LocalContext.current
        val colors = AapsTheme.generalColors
        val textColor = when (metaEntry.status) {
            PrefsStatusImpl.OK    -> colors.statusNormal
            PrefsStatusImpl.WARN  -> colors.statusWarning
            PrefsStatusImpl.ERROR -> MaterialTheme.colorScheme.error
            else                  -> MaterialTheme.colorScheme.onSurface
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val msg = if (metaEntry.info != null) {
                        "[${context.getString(metaKey.label)}] ${metaEntry.info}"
                    } else {
                        context.getString(metaKey.label)
                    }
                    when (metaEntry.status) {
                        PrefsStatusImpl.WARN  -> ToastUtils.Long.warnToast(context, msg)
                        PrefsStatusImpl.ERROR -> ToastUtils.Long.errorToast(context, msg)
                        else                  -> ToastUtils.Long.infoToast(context, msg)
                    }
                }
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(id = metaEntry.status.icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (metaEntry.status) {
                    PrefsStatusImpl.OK    -> colors.statusNormal
                    PrefsStatusImpl.WARN  -> colors.statusWarning
                    PrefsStatusImpl.ERROR -> MaterialTheme.colorScheme.error
                    else                  -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                painter = painterResource(id = metaKey.icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = metaKey.formatForDisplay(context, metaEntry.value),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun DetailsDialog(
        details: List<DetailItem>,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_header_log),
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.check_preferences_details_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(details) { detail ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${detail.label}: ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = detail.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = detail.info,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }
}