package app.aaps.wear.interaction.utils

import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventUpdateSelectedWatchface
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

abstract class MenuListActivity : DaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var elements by mutableStateOf<List<MenuItem>>(emptyList())
    private val disposable = CompositeDisposable()

    protected abstract fun provideElements(): List<MenuItem>
    protected abstract fun doAction(position: String)
    protected open fun provideTitleIcon(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disposable += rxBus
            .toObservable(EventUpdateSelectedWatchface::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { _: EventUpdateSelectedWatchface -> elements = provideElements() }
        elements = provideElements()
        val menuTitle = title.toString()
        val titleIcon = provideTitleIcon()
        setContent {
            MaterialTheme {
                MenuListScreen(
                    title = menuTitle,
                    titleIcon = titleIcon,
                    elements = elements,
                    onAction = { doAction(it) }
                )
            }
        }
    }

    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    class MenuItem(val actionIcon: Int, val actionItem: String)
}

private val MenuItemBg = Color.White.copy(alpha = 0.15f)

@Composable
private fun MenuListScreen(
    title: String,
    titleIcon: Int?,
    elements: List<MenuListActivity.MenuItem>,
    onAction: (String) -> Unit
) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader {
                    if (titleIcon != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(titleIcon),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(title)
                        }
                    } else {
                        Text(title)
                    }
                }
            }
            items(elements) { item ->
                Button(
                    onClick = { onAction(item.actionItem) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MenuItemBg,
                        contentColor = Color.White
                    ),
                    label = { Text(item.actionItem) },
                    icon = {
                        MenuIcon(
                            iconRes = item.actionIcon,
                            contentDescription = item.actionItem
                        )
                    }
                )
            }
        }
        ScrollIndicator(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun MenuIcon(iconRes: Int, contentDescription: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { 35.dp.toPx() }.toInt()
    val painter = remember(iconRes, sizePx) {
        val drawable = ContextCompat.getDrawable(context, iconRes)!!
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        BitmapPainter(bitmap.asImageBitmap())
    }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = Color.Unspecified,
        modifier = Modifier.size(35.dp)
    )
}
