package app.aaps.desktop.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "AAPS") {
        MaterialTheme {
            Text("AAPS desktop shell")
        }
    }
}
