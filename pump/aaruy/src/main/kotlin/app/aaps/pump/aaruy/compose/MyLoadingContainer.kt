package app.aaps.pump.aaruy.compose

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

val dialogWidth: Dp = 316.dp
@Composable
fun MyLoadingContainer(
    show: Boolean,
    ifShowText: Boolean = false,
    showText: String = "",
    showTipText: String = "",
    content: @Composable (() -> Unit)
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (ifShowText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { }, // 空pointerInput确保不拦截事件,
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.width(dialogWidth)
                        .heightIn(min = 67.dp)
                        .background(color = Color(0XFF222C2E), shape = RoundedCornerShape(4.dp))
                        .padding(vertical = 20.dp)
                        .padding(horizontal = 20.dp)
                        .pointerInput(Unit) { }, // 这里也不拦截事件
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = showText,
                         style = MaterialTheme.typography.headlineSmall
                    )
                    if (showTipText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = showTipText,
                             style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
        }
        if (show) {
            MyLoadingDialog(showText)
        }
    }

}

@Composable
fun MyLoadingDialog(
    content: String,
) {
    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {

            if (content.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .align(Alignment.Center)
                        .background(
                            color = Color(0XFF222C2E),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.width(16.dp))
                    CircularProgressIndicator(color = Color(0xFF00DCFF))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
            else {
                CircularProgressIndicator(color = Color(0xFF00DCFF))
            }
        }
    }
}
