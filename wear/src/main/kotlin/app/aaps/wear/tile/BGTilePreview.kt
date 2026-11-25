package app.aaps.wear.tile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

@Composable
fun BGTileMockup(
    sgv: String,
    trend: String,
    delta: String,
    iob: String,
    cob: String,
    timeAgo: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(192.dp)
            .background(Color.Black, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            Text(
                text = sgv,
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = trend, fontSize = 22.sp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = delta, fontSize = 22.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "IOB: $iob", fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "COB: $cob", fontSize = 14.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = timeAgo,
                fontSize = 12.sp,
                color = Color(0xFFB0BEC5)
            )
        }
    }
}
